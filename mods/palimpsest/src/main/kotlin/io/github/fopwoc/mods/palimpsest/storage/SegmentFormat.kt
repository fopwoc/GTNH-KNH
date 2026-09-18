package io.github.fopwoc.mods.palimpsest.storage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Segment image: magic, tile group count, then per tile a header and its records ordered by epoch
 * with per-tile epoch deltas. Sealed segments are one image named by its SHA-256; the write-ahead
 * log holds images that have not been sealed yet.
 */
internal object SegmentFormat {
    val MAGIC: ByteArray = "PALIMPSC".toByteArray(Charsets.US_ASCII)
    const val EXTENSION = ".pseg"
    private const val GROUP_HEADER_BYTES = 12
    private const val RECORD_PREFIX_BYTES = 43

    class Record(
        val key: TileKey,
        val offset: Long,
        val length: Int,
        val kind: Int,
        val epoch: Long,
        val coverage: LongArray,
    )

    class Image(val bytes: ByteArray, val records: List<Record>) {
        val sha256Name: String
            get() =
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it)
                } + EXTENSION
    }

    fun interface RecordSink {
        fun record(
            key: TileKey,
            epoch: Long,
            offset: Long,
            length: Int,
            coverage: LongArray,
            kind: Int,
        )
    }

    /** Record offsets are relative to the image start. */
    fun encode(layers: List<TileLayer>): Image {
        val groups = layers.groupBy(TileLayer::key).toSortedMap(compareBy(TileKey::z, TileKey::x))
        val records = ArrayList<Record>(layers.size)
        val bodies = ArrayList<ByteArray>(layers.size + groups.size + 2)
        var offset = 0L
        fun put(bytes: ByteArray) {
            bodies += bytes
            offset += bytes.size
        }
        put(MAGIC)
        put(leInt(groups.size))
        for ((key, history) in groups) {
            put(
                ByteBuffer.allocate(GROUP_HEADER_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(key.x)
                    .putInt(key.z)
                    .putInt(history.size)
                    .array()
            )
            var previousEpoch = 0L
            for (layer in history.sortedBy(TileLayer::epoch)) {
                val body = AdaptiveLayerCodec.encode(layer, layer.epoch - previousEpoch)
                records +=
                    Record(
                        key,
                        offset,
                        body.size,
                        body[0].toInt() and 255,
                        layer.epoch,
                        layer.coverage.copyOf(),
                    )
                put(body)
                previousEpoch = layer.epoch
            }
        }
        val bytes = ByteArray(offset.toInt())
        var at = 0
        for (body in bodies) {
            body.copyInto(bytes, at)
            at += body.size
        }
        return Image(bytes, records)
    }

    /** Walks an image at [base] in [channel]; emitted offsets are absolute channel positions. */
    @Suppress("ThrowsCount")
    fun parse(channel: FileChannel, base: Long, size: Long, sink: RecordSink) {
        val end = base + size
        if (size < MAGIC.size + Int.SIZE_BYTES)
            throw CorruptHistoryException("Truncated segment image")
        val head = ByteBuffer.allocate(MAGIC.size + Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, base, head)
        if (!head.array().copyOf(MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptHistoryException("Unsupported segment format")
        }
        val groupCount = head.getInt(MAGIC.size)
        if (groupCount < 1 || groupCount > (size - head.capacity()) / (GROUP_HEADER_BYTES + 3)) {
            throw CorruptHistoryException("Invalid tile group count")
        }
        var offset = base + head.capacity()
        val prefix = ByteArray(RECORD_PREFIX_BYTES)
        repeat(groupCount) {
            if (offset + GROUP_HEADER_BYTES > end)
                throw CorruptHistoryException("Truncated tile group")
            val group = ByteBuffer.allocate(GROUP_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            readFully(channel, offset, group)
            val key = TileKey(group.getInt(0), group.getInt(4))
            val recordCount = group.getInt(8)
            offset += GROUP_HEADER_BYTES
            if (recordCount < 1 || recordCount > (end - offset) / 3) {
                throw CorruptHistoryException("Invalid tile record count")
            }
            var previousEpoch = 0L
            repeat(recordCount) { position ->
                val available = minOf(prefix.size.toLong(), end - offset).toInt()
                readFully(channel, offset, ByteBuffer.wrap(prefix, 0, available))
                val length = AdaptiveLayerCodec.recordLength(prefix, available)
                if (length !in 3..AdaptiveLayerCodec.MAX_BYTES || offset + length > end) {
                    throw CorruptHistoryException("Invalid adaptive record")
                }
                val body = ByteArray(length)
                val copied = minOf(available, length)
                prefix.copyInto(body, endIndex = copied)
                if (copied < length) {
                    readFully(
                        channel,
                        offset + copied,
                        ByteBuffer.wrap(body, copied, length - copied),
                    )
                }
                val metadata = AdaptiveLayerCodec.indexMetadata(body, previousEpoch)
                if (position > 0 && metadata.epoch <= previousEpoch) {
                    throw CorruptHistoryException("Non-increasing tile epoch")
                }
                sink.record(
                    key,
                    metadata.epoch,
                    offset,
                    length,
                    metadata.coverage,
                    body[0].toInt() and 255,
                )
                previousEpoch = metadata.epoch
                offset += length
            }
        }
        if (offset != end) throw CorruptHistoryException("Unexpected bytes after tile groups")
    }

    fun readFully(channel: FileChannel, position: Long, buffer: ByteBuffer) {
        var offset = position
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, offset)
            if (count <= 0) throw java.io.IOException("Unexpected end of segment")
            offset += count
        }
    }

    private fun leInt(value: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
