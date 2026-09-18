package io.github.fopwoc.mods.framework.world

/**
 * Turns a chunk into 256 palette bytes as seen from [ceiling] downward: the vanilla map recipe (top
 * opaque block's map color, shaded by the slope towards north, water shaded by depth) with one
 * addition — the ceiling, so the same scan gives the surface at 255 and a cave level at 40.
 *
 * Palette byte = map color index × 4 + shade, the exact encoding of vanilla map items, so a
 * 256-entry palette covers every value.
 */
object SliceScanner {
    class Result(val colors: ByteArray, val heights: IntArray)

    private const val SHADE_DARK = 0
    private const val SHADE_NORMAL = 1
    private const val SHADE_LIGHT = 2
    private const val SLOPE_THRESHOLD = 0.6
    private const val DITHER = 0.4

    /**
     * [northHeights] are the heights the chunk to the north found for its last row (z = 15) at the
     * same ceiling, so this chunk's first row shades continuously; null shades it as flat.
     */
    fun scan(columns: ChunkColumns, ceiling: Int, northHeights: IntArray? = null): Result {
        require(ceiling in 0..columns.topY)
        require(northHeights == null || northHeights.size == ChunkColumns.SIDE)
        val colors = ByteArray(ChunkColumns.COLUMNS)
        val heights = IntArray(ChunkColumns.COLUMNS)
        for (x in 0 until ChunkColumns.SIDE) {
            var previous = northHeights?.get(x)?.toDouble()
            for (z in 0 until ChunkColumns.SIDE) {
                val at = z * ChunkColumns.SIDE + x
                val top = topBlock(columns, x, z, ceiling)
                heights[at] = top.height
                if (top.color == ChunkColumns.TRANSPARENT) {
                    colors[at] = 0
                    previous = null
                    continue
                }
                val dither = ((x + z and 1) - 0.5) * DITHER
                val shade =
                    when {
                        top.liquidDepth > 0 -> waterShade(top.liquidDepth, x + z and 1)
                        previous == null -> SHADE_NORMAL
                        else -> slopeShade((top.height - previous) * 4.0 / 5.0 + dither)
                    }
                previous = top.height.toDouble()
                colors[at] = (top.color * 4 + shade).toByte()
            }
        }
        return Result(colors, heights)
    }

    private class Top(val color: Int, val height: Int, val liquidDepth: Int)

    private fun topBlock(columns: ChunkColumns, x: Int, z: Int, ceiling: Int): Top {
        // Nothing with a map color sits above the sky-light surface, so start at the lower of the
        // two.
        var y = minOf(ceiling, columns.surfaceY(x, z))
        while (y >= 0) {
            if (columns.isSectionEmpty(y shr 4)) {
                y = (y shr 4) * ChunkColumns.SIDE - 1
                continue
            }
            val color = columns.colorIndex(x, y, z)
            if (color != ChunkColumns.TRANSPARENT) {
                var depth = 0
                if (columns.isLiquid(x, y, z)) {
                    var below = y - 1
                    while (below >= 0 && columns.isLiquid(x, below, z)) {
                        depth++
                        below--
                    }
                    depth++
                }
                return Top(color, y, depth)
            }
            y--
        }
        return Top(ChunkColumns.TRANSPARENT, -1, 0)
    }

    private fun slopeShade(slope: Double): Int =
        when {
            slope > SLOPE_THRESHOLD -> SHADE_LIGHT
            slope < -SLOPE_THRESHOLD -> SHADE_DARK
            else -> SHADE_NORMAL
        }

    private fun waterShade(depth: Int, checker: Int): Int {
        val value = depth * 0.1 + checker * 0.2
        return when {
            value < 0.5 -> SHADE_LIGHT
            value > 0.9 -> SHADE_DARK
            else -> SHADE_NORMAL
        }
    }
}
