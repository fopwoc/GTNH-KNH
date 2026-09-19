package io.github.fopwoc.mods.palimpsest.render

/**
 * Facts to pixels: the block's frozen color, the biome's tint when the block takes one, then
 * relief — flat ground at full brightness, a slope rising towards the south lighter and one
 * falling darker (the vanilla map's ratios, but nothing is dimmed by default), water fading with
 * depth, a checkerboard dither breaking up the bands. Every rule lives here and nowhere in the
 * history, so changing the look repaints the past too.
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
                    else ->
                        slopeShade(
                            (grid.height[at] - grid.height[grid.index(x, z - 1)]) * 4.0 / 5.0 +
                                (checker - 0.5) * DITHER
                        )
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

    /** Shallow water at full brightness fading to [DEEP_WATER] by [DEEP_WATER_DEPTH] blocks. */
    private fun waterShade(depth: Int, checker: Int): Int {
        val fade = (255 - DEEP_WATER) * depth.coerceAtMost(DEEP_WATER_DEPTH) / DEEP_WATER_DEPTH
        return 255 - fade - checker * WATER_DITHER
    }

    companion object {
        /** Darker, flat, lighter: the vanilla map's ratios around full brightness. */
        val SHADES = intArrayOf(208, 255, 296)
        private const val SLOPE_THRESHOLD = 0.6
        private const val DITHER = 0.4
        private const val DEEP_WATER = 160
        private const val DEEP_WATER_DEPTH = 24
        private const val WATER_DITHER = 6
        private const val WHITE = 0xFFFFFF

        /** Multiplies the color channels by `factor / 255`, clamped, keeping alpha. */
        fun shade(argb: Int, factor: Int): Int {
            if (factor == 255) return argb
            val r = ((argb shr 16 and 255) * factor / 255).coerceAtMost(255)
            val g = ((argb shr 8 and 255) * factor / 255).coerceAtMost(255)
            val b = ((argb and 255) * factor / 255).coerceAtMost(255)
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
