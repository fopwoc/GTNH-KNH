package io.github.fopwoc.mods.palimpsest.render

/**
 * Facts to pixels: the block's frozen color, the biome's tint when the block takes one, then the
 * vanilla map recipe — a slope towards the north shades lighter or darker, water shades by depth,
 * a checkerboard dither breaks up the bands. Every rule lives here and nowhere in the history, so
 * changing the look repaints the past too.
 */
class TerrainShader(
    private val color: (block: Int) -> Int,
    private val tintable: (block: Int) -> Boolean,
    private val biomeTint: (biome: Int) -> Int,
) {
    /** Fills [rgba] (side × side × 4) from the grid; absent cells stay fully transparent. */
    fun shade(grid: SampleGrid, rgba: ByteArray) {
        val side = grid.side
        require(rgba.size == side * side * 4)
        for (z in 0 until side) for (x in 0 until side) {
            val target = (z * side + x) * 4
            val at = grid.index(x, z)
            val block = grid.block[at]
            if (block <= 0) {
                rgba.fill(0, target, target + 4)
                continue
            }
            var argb = color(block) or (0xFF shl 24)
            if (tintable(block)) argb = applyTint(argb, biomeTint(grid.biome[at]))
            val checker = (x + z) and 1
            val depth = grid.depth[at]
            val factor =
                when {
                    depth > 0 -> waterShade(depth, checker)
                    !grid.isPresent(x, z - 1) -> SHADES[1]
                    else -> slopeShade((grid.height[at] - grid.height[grid.index(x, z - 1)]) * 4.0 / 5.0 + (checker - 0.5) * DITHER)
                }
            val shaded = shade(argb, factor)
            rgba[target] = (shaded ushr 16).toByte()
            rgba[target + 1] = (shaded ushr 8).toByte()
            rgba[target + 2] = shaded.toByte()
            rgba[target + 3] = -1
        }
    }

    private fun slopeShade(slope: Double): Int =
        when {
            slope > SLOPE_THRESHOLD -> SHADES[2]
            slope < -SLOPE_THRESHOLD -> SHADES[0]
            else -> SHADES[1]
        }

    private fun waterShade(depth: Int, checker: Int): Int {
        val value = depth * 0.1 + checker * 0.2
        return when {
            value < 0.5 -> SHADES[2]
            value > 0.9 -> SHADES[0]
            else -> SHADES[1]
        }
    }

    companion object {
        /** Darker, flat, lighter — the vanilla map's three brightness steps. */
        val SHADES = intArrayOf(180, 220, 255)
        private const val SLOPE_THRESHOLD = 0.6
        private const val DITHER = 0.4
        private const val WHITE = 0xFFFFFF

        /** Multiplies the color channels by `factor / 255`, keeping alpha. */
        fun shade(argb: Int, factor: Int): Int {
            val r = (argb shr 16 and 255) * factor / 255
            val g = (argb shr 8 and 255) * factor / 255
            val b = (argb and 255) * factor / 255
            return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }

        /** Multiplies an opaque color by a biome tint, keeping alpha. */
        fun applyTint(argb: Int, tint: Int): Int {
            if (tint == WHITE) return argb
            val r = (argb shr 16 and 255) * (tint shr 16 and 255) / 255
            val g = (argb shr 8 and 255) * (tint shr 8 and 255) / 255
            val b = (argb and 255) * (tint and 255) / 255
            return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }
    }
}
