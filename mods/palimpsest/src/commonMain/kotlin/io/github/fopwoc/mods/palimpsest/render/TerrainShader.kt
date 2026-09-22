package io.github.fopwoc.mods.palimpsest.render

/**
 * Facts to pixels: the block's frozen color, the biome's tint when the block takes one, then relief
 * — a hillshade lit from the north-west over the recorded heights — and water: the biome's water
 * colour laid over the floor, thin and see-through in the shallows, opaque and darker as it gets
 * deep, with the seabed's relief showing through. Every rule lives here and nowhere in the history,
 * so changing the look repaints the past too.
 */
class TerrainShader(
    private val color: (block: Int) -> Int,
    /** 0 none, 1 grass colour, 2 foliage colour. */
    private val tint: (block: Int) -> Int,
    private val grassTint: (biome: Int) -> Int,
    private val foliageTint: (biome: Int) -> Int = grassTint,
    /** The biome's water colour multiplier; white for plain water. */
    private val waterTint: (biome: Int) -> Int = { WHITE },
    /** The colour of water itself before the biome's multiplier. */
    private val waterColor: Int = WATER,
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
            when (tint(block)) {
                GRASS -> argb = applyTint(argb, grassTint(grid.biome[at]))
                FOLIAGE -> argb = applyTint(argb, foliageTint(grid.biome[at]))
            }
            val checker = (x + z) and 1
            val depth = grid.depth[at]
            val height = grid.height[at]
            val west =
                if (grid.isPresent(x - 1, z)) height - grid.height[grid.index(x - 1, z)] else 0
            val north =
                if (grid.isPresent(x, z - 1)) height - grid.height[grid.index(x, z - 1)] else 0
            var shaded = shade(argb, hillshade(west, north, checker))
            if (depth > 0) {
                // The floor's relief is muted under water: it should read as depth, not bumps.
                val floor = shade(argb, (255 + hillshade(west, north, checker)) / 2)
                val water =
                    shade(
                        applyTint(waterColor or (0xFF shl 24), waterTint(grid.biome[at])),
                        waterShade(depth, checker),
                    )
                shaded = blend(water, floor, waterOpacity(depth))
            }
            rgba[target] = (shaded ushr 16).toByte()
            rgba[target + 1] = (shaded ushr 8).toByte()
            rgba[target + 2] = shaded.toByte()
            rgba[target + 3] = -1
        }
    }

    /** Shallow water at full brightness fading to [DEEP_WATER] by [DEEP_WATER_DEPTH] blocks. */
    private fun waterShade(depth: Int, checker: Int): Int {
        val fade = (255 - DEEP_WATER) * depth.coerceAtMost(DEEP_WATER_DEPTH) / DEEP_WATER_DEPTH
        return 255 - fade - checker * WATER_DITHER
    }

    /** How much of the water hides the floor: see-through at the edge, opaque by [OPAQUE_DEPTH]. */
    private fun waterOpacity(depth: Int): Int =
        (SHALLOW_OPACITY +
                (255 - SHALLOW_OPACITY) * depth.coerceAtMost(OPAQUE_DEPTH) / OPAQUE_DEPTH)
            .coerceAtMost(255)

    companion object {
        /** Darkest, flat, lightest brightness factor over 255. */
        val SHADES = intArrayOf(170, 255, 320)
        /** Brightness per block of rise against a neighbour. */
        private const val SLOPE_GAIN = 18.0
        /** Rises beyond this many blocks shade no further. */
        private const val SLOPE_CLAMP = 3
        private const val DITHER = 6.0
        private const val DEEP_WATER = 150
        private const val DEEP_WATER_DEPTH = 24
        private const val WATER_DITHER = 6
        /** A saturated water blue; the texture average is greyer than water looks in the sun. */
        const val WATER = 0x3060E0
        private const val SHALLOW_OPACITY = 150
        private const val OPAQUE_DEPTH = 5
        private const val WHITE = 0xFFFFFF
        private const val GRASS = 1
        private const val FOLIAGE = 2

        /**
         * Light from the north-west: a cell higher than its western and northern neighbours faces
         * the light and brightens, one lower than them sits in their shadow and darkens, in
         * proportion and clamped, with a checkerboard dither so one-block steps do not band. Every
         * tree canopy gets a lit north-west edge and a shaded south-east one, which is what makes a
         * forest read as trees.
         */
        fun hillshade(west: Int, north: Int, checker: Int): Int {
            val rise =
                (west.coerceIn(-SLOPE_CLAMP, SLOPE_CLAMP) +
                    north.coerceIn(-SLOPE_CLAMP, SLOPE_CLAMP)) * SLOPE_GAIN
            if (rise == 0.0) return SHADES[1]
            return (255 + rise + (checker - 0.5) * DITHER).toInt().coerceIn(SHADES[0], SHADES[2])
        }

        /** Multiplies the color channels by `factor / 255`, clamped, keeping alpha. */
        fun shade(argb: Int, factor: Int): Int {
            if (factor == 255) return argb
            val r = ((argb shr 16 and 255) * factor / 255).coerceAtMost(255)
            val g = ((argb shr 8 and 255) * factor / 255).coerceAtMost(255)
            val b = ((argb and 255) * factor / 255).coerceAtMost(255)
            return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }

        /** [over] on top of [under] with [alpha] out of 255; opaque result. */
        fun blend(over: Int, under: Int, alpha: Int): Int {
            val r = ((over shr 16 and 255) * alpha + (under shr 16 and 255) * (255 - alpha)) / 255
            val g = ((over shr 8 and 255) * alpha + (under shr 8 and 255) * (255 - alpha)) / 255
            val b = ((over and 255) * alpha + (under and 255) * (255 - alpha)) / 255
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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
