package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.utils.LongLongMap
import java.nio.file.Path

/**
 * Rewrites a dimension's whole history into one new segment, at ordinal 0, before its session
 * writes anything: blob positions name a segment by its place in the manifest, so later segments
 * would point into any single rewritten one. Blobs are copied as stored bytes, never decoded, and
 * every reference is moved to the copy. Sealed files are never touched; the caller swaps them out
 * through the manifest.
 */
internal class Compactor(
    private val segments: List<SegmentFile>,
    private val progress: (bytes: Long) -> Unit = {},
) {
    private class Frame(val decoded: CommitRecord.Decoded, val blobBytes: ByteArray)

    /** Every commit, kept: many small segments become one. Deltas keep their bases, moved too. */
    fun history(target: Path, dimension: DimensionId, session: Manifest.Session): SegmentFile {
        val out = SegmentFile.create(target, 0, PalimpsestDb.GENERATION, dimension, session)
        val moved = LongLongMap(1 shl 16)
        forEachFrame { frame ->
            val decoded = frame.decoded
            val sink = ByteSink(frame.blobBytes.size + decoded.patches.size * 32 + 32)
            sink.varint(frame.blobBytes.size.toLong())
            val blobsStart = out.length + Frames.HEADER + sink.size
            sink.bytes(frame.blobBytes)
            var offset = blobsStart
            val positions =
                LongArray(decoded.blobs.size) { index ->
                    Positions.of(0, offset).also {
                        moved.put(decoded.blobs[index], it)
                        offset += decoded.blobLengths[index]
                    }
                }
            // A base is always an earlier commit's blob, so it has moved already.
            val bases =
                LongArray(decoded.bases.size) {
                    decoded.bases[it].let { base ->
                        if (base == Positions.AIR) base else moved.get(base)!!
                    }
                }
            CommitRecord.encodeStored(
                sink,
                decoded.tick,
                decoded.observedAt,
                positions,
                decoded.blobLengths,
                bases,
                decoded.baseLengths,
                decoded.patches.map { it.moved(moved) },
            )
            out.append(sink.toByteArray())
        }
        return out
    }

    /**
     * Only each chunk's latest version, committed at the tick it was last changed: the history
     * before it is gone, and so is every blob only that history used. A delta loses its base with
     * that history, so it is stored as the full section it stands for.
     */
    fun latest(target: Path, dimension: DimensionId, session: Manifest.Session): SegmentFile {
        val latest = HashMap<ChunkPos, Pair<Long, CommitRecord.Patch>>()
        val observedAt = HashMap<Long, Long>()
        val bases = HashMap<Long, LongArray>()
        forEachFrame(withBlobs = false) { frame ->
            val decoded = frame.decoded
            observedAt[decoded.tick] = decoded.observedAt
            for (index in decoded.blobs.indices) if (decoded.bases[index] != Positions.AIR)
                bases[decoded.blobs[index]] =
                    longArrayOf(decoded.bases[index], decoded.baseLengths[index].toLong())
            for (patch in decoded.patches) {
                val previous = latest[patch.pos]?.second?.takeIf { it.slots == patch.slots }
                val positions =
                    previous?.positions?.copyOf() ?: LongArray(patch.slots) { Positions.AIR }
                val lengths = previous?.lengths?.copyOf() ?: IntArray(patch.slots)
                for (slot in 0 until patch.slots) {
                    if (patch.mask and (1L shl slot) == 0L) continue
                    positions[slot] = patch.positions[slot]
                    lengths[slot] = patch.lengths[slot]
                }
                latest[patch.pos] =
                    decoded.tick to
                        CommitRecord.Patch(
                            patch.pos,
                            patch.minSection,
                            patch.slots,
                            (1L shl patch.slots) - 1,
                            positions,
                            lengths,
                        )
            }
        }
        val reader = BlobReader({ segments }, capacity = 256)
        val out = SegmentFile.create(target, 0, PalimpsestDb.GENERATION, dimension, session)
        val moved = LongLongMap(1 shl 16)
        val newLengths = HashMap<Long, Int>()
        for ((tick, chunks) in latest.values.groupBy({ it.first }, { it.second }).toSortedMap()) {
            val blobs = ByteSink(chunks.size * 1024)
            val fresh = ArrayList<Long>()
            val lengths = ArrayList<Int>()
            val taken = HashSet<Long>()
            for (patch in chunks) {
                for (slot in 0 until patch.slots) {
                    val old = patch.positions[slot]
                    if (old == Positions.AIR || moved.get(old) != null || !taken.add(old)) continue
                    val bytes =
                        if (old !in bases)
                            segments[Positions.segment(old)].read(
                                Positions.offset(old),
                                patch.lengths[slot],
                            )
                        else
                            ByteSink(512)
                                .also {
                                    SectionCodec.encode(
                                        reader.decode(
                                            old,
                                            patch.lengths[slot],
                                            BlobKind.SECTION,
                                            bases::get,
                                        ),
                                        it,
                                    )
                                }
                                .toByteArray()
                    fresh += old
                    lengths += bytes.size
                    blobs.bytes(bytes)
                }
            }
            val sink = ByteSink(blobs.size + chunks.size * 64 + 32)
            sink.varint(blobs.size.toLong())
            var offset = out.length + Frames.HEADER + sink.size
            val positions =
                LongArray(fresh.size) { index ->
                    Positions.of(0, offset).also {
                        moved.put(fresh[index], it)
                        newLengths[fresh[index]] = lengths[index]
                        offset += lengths[index]
                    }
                }
            sink.bytes(blobs.toByteArray())
            CommitRecord.encodeStored(
                sink,
                tick,
                observedAt.getValue(tick),
                positions,
                lengths.toIntArray(),
                LongArray(fresh.size) { Positions.AIR },
                IntArray(fresh.size),
                chunks.map { patch -> patch.moved(moved, newLengths) },
            )
            out.append(sink.toByteArray())
        }
        return out
    }

    /**
     * The patch with its blobs at their copies; [rewritten] has new lengths of blobs rewritten
     * whole.
     */
    private fun CommitRecord.Patch.moved(
        moved: LongLongMap,
        rewritten: Map<Long, Int> = emptyMap(),
    ): CommitRecord.Patch =
        CommitRecord.Patch(
            pos,
            minSection,
            slots,
            mask,
            LongArray(slots) { slot ->
                val old = positions[slot]
                if (mask and (1L shl slot) == 0L || old == Positions.AIR) old
                else checkNotNull(moved.get(old)) { "Blob at $old was never copied" }
            },
            IntArray(slots) { slot -> rewritten[positions[slot]] ?: lengths[slot] },
        )

    private fun forEachFrame(withBlobs: Boolean = true, action: (Frame) -> Unit) {
        for (segment in segments) {
            var at = SegmentFile.firstFrame(segment)
            while (at < segment.length) {
                val payloadLength = segment.frameLength(at)
                val payloadStart = at + Frames.HEADER
                val payload = segment.read(payloadStart, payloadLength)
                val source = ByteSource(payload)
                val blobsLength = source.varintInt()
                val blobsAt = source.position
                val blobBytes =
                    if (withBlobs) payload.copyOfRange(blobsAt, blobsAt + blobsLength)
                    else ByteArray(0)
                val record = ByteSource(payload, blobsAt + blobsLength, payload.size)
                action(
                    Frame(
                        CommitRecord.decode(record, segment.ordinal, payloadStart + blobsAt),
                        blobBytes,
                    )
                )
                progress(Frames.HEADER + payloadLength.toLong())
                at = payloadStart + payloadLength
            }
        }
    }

    companion object {
        const val SUFFIX = "-compact.seg"
    }
}
