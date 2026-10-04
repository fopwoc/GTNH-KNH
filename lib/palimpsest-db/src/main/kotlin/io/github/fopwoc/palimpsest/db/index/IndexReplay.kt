package io.github.fopwoc.palimpsest.db.index

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

/**
 * Brings [index] up to date with truth: applies every commit frame past its coverage. Content
 * hashes are not truth, so each blob the frames reference is decoded and hashed, on the common
 * pool, with recently hashed positions remembered: a full rebuild touches every stored blob once,
 * catching up after a crash only a few frames.
 */
internal class IndexReplay(
    private val index: DimensionIndex,
    private val segments: List<SegmentFile>,
) {
    private val reader = BlobReader({ segments }, capacity = 64)
    private val hashes =
        object : LinkedHashMap<Long, ContentHash>(4096, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ContentHash>) =
                size > HASHES
        }

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
        val frameHashes = hashAll(decoded)
        for (patch in decoded.patches) {
            val previous = index.latest(patch.pos)?.takeIf { Versions.slots(it) == patch.slots }
            val version = Versions.empty(decoded.tick, patch.minSection, patch.slots)
            for (slot in 0 until patch.slots) {
                if (patch.mask and (1L shl slot) == 0L)
                    previous?.let { Versions.copy(it, version, slot) }
                else if (patch.positions[slot] != Positions.AIR)
                    Versions.set(
                        version,
                        slot,
                        patch.positions[slot],
                        patch.lengths[slot],
                        frameHashes.getValue(patch.positions[slot]),
                    )
            }
            index.append(patch.pos, version)
        }
        val sections = decoded.blobLengths.size
        index.append(
            Commit(
                WorldTick(decoded.tick),
                decoded.observedAt,
                decoded.patches.size,
                sections,
                Frames.HEADER + payloadLength.toLong(),
            )
        )
        return payloadStart + payloadLength
    }

    /** The hash of every blob the frame's patches reference, decoding those not remembered. */
    private fun hashAll(decoded: CommitRecord.Decoded): Map<Long, ContentHash> {
        val found = HashMap<Long, ContentHash>()
        val missing = HashMap<Long, Pair<Int, BlobKind>>()
        for (patch in decoded.patches) {
            for (slot in 0 until patch.slots) {
                val position = patch.positions[slot]
                if (
                    patch.mask and (1L shl slot) == 0L ||
                        position == Positions.AIR ||
                        position in found
                )
                    continue
                val known = hashes[position]
                if (known != null) found[position] = known
                else missing[position] = patch.lengths[slot] to BlobKind.of(slot, patch.slots)
            }
        }
        missing.entries
            .toList()
            .parallelStream()
            .map { (position, blob) ->
                val (length, kind) = blob
                position to ContentHash.of(reader.decode(position, length, kind), kind.ordinal)
            }
            .toList()
            .forEach { (position, hash) ->
                hashes[position] = hash
                found[position] = hash
            }
        return found
    }

    private companion object {
        const val VARINT_MAX = 10
        const val HASHES = 1 shl 16
    }
}
