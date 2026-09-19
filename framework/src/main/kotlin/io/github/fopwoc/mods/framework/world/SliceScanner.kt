package io.github.fopwoc.mods.framework.world

/**
 * Turns a chunk into 256 palette bytes as seen from [ceiling] downward: the vanilla map recipe (top
 * opaque block, shaded by the slope towards north, water shaded by depth) applied to real block
 * colors, with one addition — the ceiling, so the same scan gives the surface at 255 and a cave
 * level at 40. Shading happens in RGB and the result snaps to the world palette.
 */
object SliceScanner {
    class Result(
        val colors: ByteArray,
        val biomes: ByteArray,
        val argb: IntArray,
        val heights: IntArray,
    )

    private const val SLOPE_THRESHOLD = 0.6
    private const val DITHER = 0.4

    /**
     * [northHeights] are the heights the chunk to the north found for its last row (z = 15) at the
     * same ceiling, so this chunk's first row shades continuously; null shades it as flat.
     */
    fun scan(
        columns: ChunkColumns,
        ceiling: Int,
        palette: WorldPalette,
        northHeights: IntArray? = null,
    ): Result {
        require(ceiling in 0..columns.topY)
        require(northHeights == null || northHeights.size == ChunkColumns.SIDE)
        val colors = ByteArray(ChunkColumns.COLUMNS)
        val biomes = ByteArray(ChunkColumns.COLUMNS)
        val argb = IntArray(ChunkColumns.COLUMNS)
        val heights = IntArray(ChunkColumns.COLUMNS)
        for (x in 0 until ChunkColumns.SIDE) {
            var previous = northHeights?.get(x)?.toDouble()
            for (z in 0 until ChunkColumns.SIDE) {
                val at = z * ChunkColumns.SIDE + x
                biomes[at] = columns.biomeAt(x, z).toByte()
                val top = topBlock(columns, x, z, ceiling)
                heights[at] = top.height
                if (top.entry == ChunkColumns.TRANSPARENT) {
                    previous = null
                    continue
                }
                val checker = x + z and 1
                val shade =
                    when {
                        top.liquidDepth > 0 -> waterShade(top.liquidDepth, checker)
                        previous == null -> WorldPalette.SHADES[1]
                        else ->
                            slopeShade(
                                (top.height - previous) * 4.0 / 5.0 + (checker - 0.5) * DITHER
                            )
                    }
                previous = top.height.toDouble()
                val shaded = WorldPalette.shade(palette.argb(top.entry), shade)
                argb[at] = shaded
                colors[at] = palette.nearestFor(shaded, palette.isTintable(top.entry)).toByte()
            }
        }
        return Result(colors, biomes, argb, heights)
    }

    private class Top(val entry: Int, val height: Int, val liquidDepth: Int)

    private fun topBlock(columns: ChunkColumns, x: Int, z: Int, ceiling: Int): Top {
        // Nothing with a color sits above the sky-light surface, so start at the lower of the two.
        var y = minOf(ceiling, columns.surfaceY(x, z))
        while (y >= 0) {
            if (columns.isSectionEmpty(y shr 4)) {
                y = (y shr 4) * ChunkColumns.SIDE - 1
                continue
            }
            val entry = columns.entryAt(x, y, z)
            if (entry != ChunkColumns.TRANSPARENT) {
                var depth = 0
                if (columns.isLiquid(x, y, z)) {
                    depth = 1
                    var below = y - 1
                    while (below >= 0 && columns.isLiquid(x, below, z)) {
                        depth++
                        below--
                    }
                }
                return Top(entry, y, depth)
            }
            y--
        }
        return Top(ChunkColumns.TRANSPARENT, -1, 0)
    }

    private fun slopeShade(slope: Double): Int =
        when {
            slope > SLOPE_THRESHOLD -> WorldPalette.SHADES[2]
            slope < -SLOPE_THRESHOLD -> WorldPalette.SHADES[0]
            else -> WorldPalette.SHADES[1]
        }

    private fun waterShade(depth: Int, checker: Int): Int {
        val value = depth * 0.1 + checker * 0.2
        return when {
            value < 0.5 -> WorldPalette.SHADES[2]
            value > 0.9 -> WorldPalette.SHADES[0]
            else -> WorldPalette.SHADES[1]
        }
    }
}
