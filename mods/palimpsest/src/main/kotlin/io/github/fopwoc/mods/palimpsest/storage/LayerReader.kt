package io.github.fopwoc.mods.palimpsest.storage

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reconstructs tiles and pixel samples from an indexed history by reading record bodies. */
internal class LayerReader(private val channelOf: (Int) -> FileChannel) {
    fun read(key: TileKey, history: PackedTileHistory, epoch: Long): TileHistoryStore.TileRead? {
        val firstAfter = history.firstAfter(epoch)
        if (firstAfter == 0) return null
        val result = ByteArray(TileLayer.PIXELS)
        val missing = LongArray(TileLayer.MASK_WORDS) { -1L }
        val span = SpanReader(history)
        var visited = 0
        var decoded = 0
        var skipped = 0
        var entryIndex = firstAfter - 1
        while (entryIndex >= 0) {
            val group = entryIndex / PackedTileHistory.GROUP_SIZE
            if (!history.groupCanFill(group, missing)) {
                skipped += entryIndex - group * PackedTileHistory.GROUP_SIZE + 1
                entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
                continue
            }
            val groupStart = group * PackedTileHistory.GROUP_SIZE
            while (entryIndex >= groupStart) {
                visited++
                if (history.layerCanFill(entryIndex, missing)) {
                    val layer =
                        AdaptiveLayerCodec.decode(
                            span.body(entryIndex),
                            key,
                            history.epochAt(entryIndex),
                        )
                    decoded++
                    var colorIndex = 0
                    for (position in 0 until TileLayer.PIXELS) {
                        val bit = 1L shl (position and 63)
                        val word = position ushr 6
                        if (layer.coverage[word] and bit == 0L) continue
                        if (missing[word] and bit != 0L) result[position] = layer.colors[colorIndex]
                        colorIndex++
                    }
                    for (word in missing.indices) missing[word] =
                        missing[word] and layer.coverage[word].inv()
                    if (missing.all { it == 0L })
                        return TileHistoryStore.TileRead(result, visited, decoded, skipped)
                }
                entryIndex--
            }
        }
        throw CorruptHistoryException("Tile $key has no complete initial layer")
    }

    /**
     * Reads record bodies through one buffer per contiguous run: a tile's records inside a segment
     * are adjacent and reads walk newest to oldest, so one positioned read can serve many layers.
     * The span starts small and grows only while the walk keeps continuing past the buffer, so a
     * read that stops at a checkpoint after one or two layers does not fetch a whole history.
     */
    private inner class SpanReader(private val history: PackedTileHistory) {
        private var segment = -1
        private var start = 0L
        private var buffer = ByteArray(0)
        private var span = SPAN_MIN_BYTES

        fun body(entry: Int): ByteArray {
            val segmentId = history.segmentAt(entry)
            val offset = history.offsetAt(entry)
            val length = history.lengthAt(entry)
            if (segmentId != segment || offset < start || offset + length > start + buffer.size) {
                val continuing = segmentId == segment && offset + length == start
                span = if (continuing) minOf(span * 4, SPAN_MAX_BYTES) else SPAN_MIN_BYTES
                var first = entry
                var total = length
                while (first > 0 && history.segmentAt(first - 1) == segmentId) {
                    val previousLength = history.lengthAt(first - 1)
                    if (history.offsetAt(first - 1) + previousLength != history.offsetAt(first))
                        break
                    if (total + previousLength > span) break
                    first--
                    total += previousLength
                }
                segment = segmentId
                start = history.offsetAt(first)
                buffer = ByteArray(total)
                SegmentFormat.readFully(channelOf(segmentId), start, ByteBuffer.wrap(buffer))
            }
            val at = (offset - start).toInt()
            return buffer.copyOfRange(at, at + length)
        }
    }

    /** Resolves selected indexed colors using coverage masks and positional record reads. */
    fun readSamples(
        key: TileKey,
        history: PackedTileHistory,
        epoch: Long,
        positions: IntArray,
    ): TileHistoryStore.SampleRead? {
        require(positions.isNotEmpty() && positions.size <= TileLayer.PIXELS)
        require(positions.all { it in 0 until TileLayer.PIXELS })
        require((1 until positions.size).all { positions[it - 1] < positions[it] })
        var entryIndex = history.firstAfter(epoch) - 1
        if (entryIndex < 0) return null
        val missing = LongArray(TileLayer.MASK_WORDS)
        for (position in positions) {
            missing[position ushr 6] = missing[position ushr 6] or (1L shl (position and 63))
        }
        val colors = ByteArray(positions.size)
        var bytesRead = 0
        var visited = 0
        var decoded = 0
        while (entryIndex >= 0) {
            val group = entryIndex / PackedTileHistory.GROUP_SIZE
            if (!history.groupCanFill(group, missing)) {
                entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
                continue
            }
            val groupStart = group * PackedTileHistory.GROUP_SIZE
            while (entryIndex >= groupStart) {
                visited++
                if (history.layerCanFill(entryIndex, missing)) {
                    bytesRead += readLayerSamples(history, entryIndex, positions, missing, colors)
                    decoded++
                    if (missing.all { it == 0L })
                        return TileHistoryStore.SampleRead(colors, bytesRead, visited, decoded)
                }
                entryIndex--
            }
        }
        throw CorruptHistoryException("Tile $key has no complete initial layer")
    }

    @Suppress("CyclomaticComplexMethod", "ThrowsCount")
    private fun readLayerSamples(
        history: PackedTileHistory,
        index: Int,
        positions: IntArray,
        missing: LongArray,
        output: ByteArray,
    ): Int {
        val length = history.lengthAt(index)
        val channel = channelOf(history.segmentAt(index))
        val kind = history.kindAt(index)
        if (kind == AdaptiveLayerCodec.FULL_EXCEPTIONS) {
            val body = ByteBuffer.allocate(length)
            SegmentFormat.readFully(channel, history.offsetAt(index), body)
            if (body.get(0).toInt() != AdaptiveLayerCodec.FULL_EXCEPTIONS) {
                throw CorruptHistoryException("Invalid exception record")
            }
            var payload = 1
            while (payload < length) {
                if (body.get(payload++).toInt() and 128 == 0) break
            }
            if (payload + 2 > length) throw CorruptHistoryException("Truncated exception record")
            val base = body.get(payload)
            val count = body.get(payload + 1).toInt() and 255
            if (count !in 1..16 || payload + 2 + count * 2 != length) {
                throw CorruptHistoryException("Invalid exception record length")
            }
            for ((resultIndex, position) in positions.withIndex()) {
                if (missing[position ushr 6] and (1L shl (position and 63)) != 0L) {
                    output[resultIndex] = base
                }
            }
            var previousPosition = -1
            repeat(count) { exception ->
                val position = body.get(payload + 2 + exception * 2).toInt() and 255
                if (position <= previousPosition) {
                    throw CorruptHistoryException("Unsorted exception positions")
                }
                val resultIndex = positions.binarySearch(position)
                if (
                    resultIndex >= 0 &&
                        missing[position ushr 6] and (1L shl (position and 63)) != 0L
                ) {
                    output[resultIndex] = body.get(payload + 3 + exception * 2)
                }
                previousPosition = position
            }
            for (position in positions) {
                missing[position ushr 6] =
                    missing[position ushr 6] and (1L shl (position and 63)).inv()
            }
            return length
        }
        val covered =
            (0 until TileLayer.MASK_WORDS).sumOf {
                java.lang.Long.bitCount(history.maskAt(index, it))
            }
        // The body length and coverage determine where colors start for every encoding.
        val colorStart =
            when (kind) {
                AdaptiveLayerCodec.SPARSE -> length - 2 * covered
                AdaptiveLayerCodec.FULL_SOLID,
                AdaptiveLayerCodec.MASKED_SOLID -> length - 1
                else -> length - covered
            }
        val selectedOffsets = IntArray(positions.size) { -1 }
        var firstOffset = length
        var lastOffset = -1
        for ((resultIndex, position) in positions.withIndex()) {
            val word = position ushr 6
            val bit = 1L shl (position and 63)
            if (missing[word] and bit == 0L || history.maskAt(index, word) and bit == 0L) continue
            var rank = 0
            for (earlier in 0 until word) rank +=
                java.lang.Long.bitCount(history.maskAt(index, earlier))
            rank += java.lang.Long.bitCount(history.maskAt(index, word) and (bit - 1))
            val offset =
                when (kind) {
                    AdaptiveLayerCodec.SPARSE -> colorStart + rank * 2 + 1
                    AdaptiveLayerCodec.MASKED -> colorStart + rank
                    AdaptiveLayerCodec.FULL_SOLID,
                    AdaptiveLayerCodec.MASKED_SOLID -> colorStart
                    else -> colorStart + position
                }
            if (offset !in 0 until length) {
                throw CorruptHistoryException("Invalid sample color offset")
            }
            selectedOffsets[resultIndex] = offset
            firstOffset = minOf(firstOffset, offset)
            lastOffset = maxOf(lastOffset, offset)
        }
        if (lastOffset < 0) return 0
        val payloadBytes = ByteBuffer.allocate(lastOffset - firstOffset + 1)
        SegmentFormat.readFully(channel, history.offsetAt(index) + firstOffset, payloadBytes)
        for ((resultIndex, offset) in selectedOffsets.withIndex()) {
            if (offset < 0) continue
            val position = positions[resultIndex]
            output[resultIndex] = payloadBytes.get(offset - firstOffset)
            missing[position ushr 6] = missing[position ushr 6] and (1L shl (position and 63)).inv()
        }
        return payloadBytes.capacity()
    }

    private companion object {
        const val SPAN_MIN_BYTES = 2 shl 10
        const val SPAN_MAX_BYTES = 64 shl 10
    }
}
