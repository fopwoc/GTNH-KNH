package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.codec.ByteSource

/**
 * One commit frame of a segment, from [start] until [end]: its decoded [record], where its blobs
 * begin in the file, and the blob bytes themselves when they were asked for.
 */
internal class CommitFrame(
    val start: Long,
    val end: Long,
    val blobsStart: Long,
    val blobs: ByteArray?,
    val record: CommitRecord.Decoded,
) {
    companion object {
        /**
         * The frame at [at]. Without [withBlobs] only the record is read, skipping the blob bytes
         * that make up most of a frame.
         */
        fun read(segment: SegmentFile, at: Long, withBlobs: Boolean): CommitFrame {
            val payloadLength = segment.frameLength(at)
            val payloadStart = at + Frames.HEADER
            val end = payloadStart + payloadLength
            if (withBlobs) {
                val payload = segment.read(payloadStart, payloadLength)
                val source = ByteSource(payload)
                val blobsLength = source.varintInt()
                val blobsAt = source.position
                val record = ByteSource(payload, blobsAt + blobsLength, payload.size)
                return CommitFrame(
                    at,
                    end,
                    payloadStart + blobsAt,
                    payload.copyOfRange(blobsAt, blobsAt + blobsLength),
                    CommitRecord.decode(record, segment.ordinal, payloadStart + blobsAt),
                )
            }
            val head = ByteSource(segment.read(payloadStart, minOf(VARINT_MAX, payloadLength)))
            val blobsLength = head.varint()
            val blobsStart = payloadStart + head.position
            val recordStart = blobsStart + blobsLength
            val record = segment.read(recordStart, (end - recordStart).toInt())
            return CommitFrame(
                at,
                end,
                blobsStart,
                null,
                CommitRecord.decode(ByteSource(record), segment.ordinal, blobsStart),
            )
        }

        /** Every commit frame of [segment] from [from], in order. */
        fun all(
            segment: SegmentFile,
            from: Long = SegmentFile.firstFrame(segment),
            withBlobs: Boolean,
        ): Sequence<CommitFrame> =
            generateSequence(if (from < segment.length) read(segment, from, withBlobs) else null) {
                previous ->
                if (previous.end < segment.length) read(segment, previous.end, withBlobs) else null
            }

        private const val VARINT_MAX = 10
    }
}
