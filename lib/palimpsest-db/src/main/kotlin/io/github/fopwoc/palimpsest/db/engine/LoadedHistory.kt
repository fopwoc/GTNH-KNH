package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.CommitRecord
import io.github.fopwoc.palimpsest.db.store.Frames
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import java.util.concurrent.ConcurrentHashMap

/**
 * A dimension's history as read back from its segments: every chunk version, the content table for
 * deduplication and the timeline. Reading skips blob bytes and decodes only commit records.
 */
internal class LoadedHistory(
    val histories: ConcurrentHashMap<ChunkPos, ChunkHistory> = ConcurrentHashMap(),
    val content: ConcurrentHashMap<ContentHash, BlobRef> = ConcurrentHashMap(),
    val commits: MutableList<Commit> = ArrayList(),
) {
    /** Replays [segment]'s commits, in order, on top of what earlier segments built. */
    fun replay(segment: SegmentFile, positions: MutableMap<Long, BlobRef>) {
        var at = SegmentFile.firstFrame(segment)
        while (at < segment.length) {
            val payloadLength = segment.frameLength(at)
            val payloadStart = at + Frames.HEADER
            val head = ByteSource(segment.read(payloadStart, minOf(VARINT_MAX, payloadLength)))
            val blobsLength = head.varint()
            val blobsStart = payloadStart + head.position
            val recordStart = blobsStart + blobsLength
            val record =
                segment.read(recordStart, (payloadStart + payloadLength - recordStart).toInt())
            val decoded =
                CommitRecord.decode(
                    ByteSource(record),
                    segment.ordinal,
                    blobsStart,
                    previous = { histories[it]?.latest?.slots },
                    stored = { ordinal, offset ->
                        positions[key(ordinal, offset)]
                            ?: throw CorruptDataException("Dangling blob reference")
                    },
                )
            for (blob in decoded.blobs) {
                positions[key(blob.segment, blob.offset)] = blob
                content.putIfAbsent(blob.hash, blob)
            }
            for (patch in decoded.patches) histories
                .getOrPut(patch.pos, ::ChunkHistory)
                .restore(ChunkHistory.Version(decoded.tick, patch.minSection, patch.slots))
            commits +=
                Commit(
                    WorldTick(decoded.tick),
                    decoded.observedAt,
                    decoded.patches.size,
                    decoded.blobs.count { it.kind == BlobKind.SECTION },
                    Frames.HEADER + payloadLength.toLong(),
                )
            at = payloadStart + payloadLength
        }
    }

    private companion object {
        const val VARINT_MAX = 10

        fun key(segment: Int, offset: Long): Long = (segment.toLong() shl 40) or offset
    }
}
