package io.github.fopwoc.palimpsest.db.surface

import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ByteSource
import io.github.fopwoc.palimpsest.db.codec.ChannelCodec
import io.github.fopwoc.palimpsest.db.codec.CorruptDataException

/**
 * One chunk seen from above: per column (x fastest) the first block the map does not look through,
 * its world Y, the water depth above it and the biome. Facts, not colors.
 */
internal class Surface(
    val block: IntArray,
    val height: IntArray,
    val depth: IntArray,
    val biome: IntArray,
) {
    /**
     * Channels through [ChannelCodec], heights relative to [minY] so they fit a byte on 1.7.10.
     * Deterministic, so an unchanged surface encodes to the same bytes.
     */
    fun encode(minY: Int): ByteArray {
        val heights = IntArray(COLUMNS) { height[it] - minY }
        val wide = heights.any { it > 0xFF }
        val sink = ByteSink(256)
        sink.signed(minY.toLong())
        sink.byte(if (wide) 2 else 1)
        ChannelCodec.encode(sink, block, 2)
        ChannelCodec.encode(sink, heights, if (wide) 2 else 1)
        ChannelCodec.encode(sink, depth, 1)
        ChannelCodec.encode(sink, biome, 2)
        return sink.toByteArray()
    }

    /** The far-zoom sample: the center column's block, height, depth and biome. */
    fun sample(): IntArray = intArrayOf(block[CENTER], height[CENTER], depth[CENTER], biome[CENTER])

    companion object {
        const val COLUMNS = 256
        private const val CENTER = 8 * 16 + 8

        fun decode(bytes: ByteArray): Surface {
            val source = ByteSource(bytes)
            val minY = source.signed().toInt()
            val heightWidth = source.byte()
            if (heightWidth !in 1..2)
                throw CorruptDataException("Bad surface height width $heightWidth")
            val block = ChannelCodec.decode(source, COLUMNS, 2)
            val height =
                ChannelCodec.decode(source, COLUMNS, heightWidth).also {
                    for (i in it.indices) it[i] += minY
                }
            return Surface(
                block,
                height,
                ChannelCodec.decode(source, COLUMNS, 1),
                ChannelCodec.decode(source, COLUMNS, 2),
            )
        }
    }
}
