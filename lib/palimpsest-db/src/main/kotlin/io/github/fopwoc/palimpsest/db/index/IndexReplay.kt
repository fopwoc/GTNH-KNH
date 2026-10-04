package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.engine.BlobReader
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.CommitRecord
import io.github.fopwoc.palimpsest.db.store.Frames
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan

/**
 * Brings [index] up to date with truth: applies every commit frame past its coverage. Hashes and
 * surfaces are not truth, so every chunk version a frame introduces is decoded, once, on the common
 * pool, and both are computed from it: a full rebuild reads every stored section, catching up after
 * a crash only a few frames.
 */
internal class IndexReplay(
    private val index: DimensionIndex,
    private val segments: List<SegmentFile>,
    private val kind: (Int) -> BlockKind,
) {
    private val reader = BlobReader({ segments }, capacity = 1024)

    /** Returns the number of commit frames applied. */
    fun run(): Int {
        var frames = 0
        for (segment in segments) {
            var at =
                index.covered.getOrNull(segment.ordinal)?.length ?: SegmentFile.firstFrame(segment)
            while (at < segment.length) {
                at = apply(segment, at)
                frames++
            }
            index.cover(segment.ordinal, segment.name, segment.length)
        }
        return frames
    }

    /** Applies the commit frame at [at]; returns where the next one starts. */
    private fun apply(segment: SegmentFile, at: Long): Long {
        val payloadLength = segment.frameLength(at)
        val payloadStart = at + Frames.HEADER
        val head = ByteSource(segment.read(payloadStart, minOf(VARINT_MAX, payloadLength)))
        val blobsLength = head.varint()
        val blobsStart = payloadStart + head.position
        val recordStart = blobsStart + blobsLength
        val record = segment.read(recordStart, (payloadStart + payloadLength - recordStart).toInt())
        val decoded = CommitRecord.decode(ByteSource(record), segment.ordinal, blobsStart)

        // Positions first: unchanged slots come from the previous version, already indexed.
        val versions =
            decoded.patches.map { patch ->
                val previous = index.latest(patch.pos)?.takeIf { Versions.slots(it) == patch.slots }
                Versions.empty(decoded.tick, patch.minSection, patch.slots).also { version ->
                    for (slot in 0 until patch.slots) {
                        if (patch.mask and (1L shl slot) == 0L)
                            previous?.let { Versions.copy(it, version, slot) }
                        else if (patch.positions[slot] != Positions.AIR)
                            Versions.set(
                                version,
                                slot,
                                patch.positions[slot],
                                patch.lengths[slot],
                                null,
                            )
                    }
                }
            }
        val contents = decodeAll(versions)
        val surfaces =
            decoded.patches.indices
                .toList()
                .parallelStream()
                .map { i ->
                    val version = versions[i]
                    val mask = decoded.patches[i].mask
                    val slots = Versions.slots(version)
                    for (slot in 0 until slots) {
                        val position = Versions.position(version, slot)
                        if (position == Positions.AIR || mask and (1L shl slot) == 0L) continue
                        val hash =
                            ContentHash.of(
                                contents.getValue(position),
                                BlobKind.of(slot, slots).ordinal,
                            )
                        Versions.set(version, slot, position, Versions.length(version, slot), hash)
                    }
                    val sections =
                        Array(slots - 1) { slot ->
                            Versions.position(version, slot)
                                .takeIf { it != Positions.AIR }
                                ?.let(contents::getValue)
                        }
                    val biomes = contents.getValue(Versions.position(version, slots - 1))
                    SurfaceScan.scan(sections, Versions.minSection(version), biomes, kind)
                }
                .toList()
        decoded.patches.forEachIndexed { i, patch ->
            index.append(
                patch.pos,
                versions[i],
                surfaces[i].encode(patch.minSection * 16),
                surfaces[i].sample(),
            )
        }
        val sections = decoded.blobLengths.size
        index.append(
            Commit(
                WorldTick(decoded.tick),
                decoded.observedAt,
                decoded.patches.size,
                sections,
                Frames.HEADER + payloadLength.toLong(),
            ),
            decoded.patches.map { RegionKey.of(it.pos) }.toSet(),
        )
        return payloadStart + payloadLength
    }

    /** Every blob the versions hold, decoded once, by position. */
    private fun decodeAll(versions: List<LongArray>): Map<Long, IntArray> {
        val wanted = HashMap<Long, Pair<Int, BlobKind>>()
        for (version in versions) {
            val slots = Versions.slots(version)
            for (slot in 0 until slots) {
                val position = Versions.position(version, slot)
                if (position != Positions.AIR)
                    wanted[position] = Versions.length(version, slot) to BlobKind.of(slot, slots)
            }
        }
        return wanted.entries
            .toList()
            .parallelStream()
            .map { (position, blob) ->
                position to reader.decode(position, blob.first, blob.second)
            }
            .toList()
            .toMap()
    }

    private companion object {
        const val VARINT_MAX = 10
    }
}
