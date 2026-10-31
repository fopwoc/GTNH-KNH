package io.github.fopwoc.mods.palimpsest.render

import kotlin.math.exp

/**
 * Facts to colours: the block's frozen color with the biome's tint when the block takes one, the
 * small relief of a [hillshade] against its neighbours, and water: the biome's water colour,
 * turquoise and see-through in the shallows, deep blue, darker and opaque as it gets deep. The
 * ground's large shapes and the light are left to the scene. Every rule lives here and nowhere in
 * the history, so changing the look repaints the past too.
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
    /** A column's own colour, unlit and opaque: the tinted block. 0 for no block. */
    fun column(block: Int, biome: Int): Int {
        if (block <= 0) return 0
        val argb = color(block) or (0xFF shl 24)
        return when (tint(block)) {
            GRASS -> applyTint(argb, grassTint(biome))
            FOLIAGE -> applyTint(argb, foliageTint(biome))
            else -> argb
        }
    }

    /** [biome]'s water before depth, opaque: what [water] colours by how deep it is. */
    fun waterColor(biome: Int): Int = applyTint(waterColor or (0xFF shl 24), waterTint(biome))

    /** [depth] blocks of [biome]'s water, as [water] over [waterColor]. */
    fun water(depth: Double, biome: Int): Int = water(waterColor(biome), depth)

    companion object {
        /** Darkest, flat, lightest brightness factor over 255. */
        val SHADES = intArrayOf(170, 255, 320)
        /** Brightness per block of rise against a neighbour. */
        private const val SLOPE_GAIN = 18.0
        /** Rises beyond this many blocks shade no further. */
        private const val SLOPE_CLAMP = 3
        private const val DITHER = 6.0
        /** A saturated water blue; the texture average is greyer than water looks in the sun. */
        const val WATER = 0x3060E0
        private const val DEEP_WATER = 150
        private const val DEEP_WATER_DEPTH = 24.0
        /** How see-through water is at its very edge, and how fast it fills in with depth. */
        private const val EDGE_OPACITY = 0.3
        private const val OPACITY_DEPTH = 2.5
        /** Shallows let the floor's light back through green: red and blue fade first. */
        private const val SHALLOW_RED = 0.8
        private const val SHALLOW_GREEN = 1.45
        private const val SHALLOW_BLUE = 0.95
        private const val SHALLOW_DEPTH = 3.0
        private const val WHITE = 0xFFFFFF
        private const val GRASS = 1
        private const val FOLIAGE = 2

        /**
         * [depth] blocks of water of colour [tinted], fractions allowed for the corners between
         * columns: its alpha how much of the floor it hides, from [EDGE_OPACITY] at the edge to all
         * of it in the deep.
         */
        fun water(tinted: Int, depth: Double): Int {
            val deep = depth.coerceAtLeast(0.0)
            val shallow = exp(-deep / SHALLOW_DEPTH)
            val dark = 255 - (255 - DEEP_WATER) * (deep / DEEP_WATER_DEPTH).coerceAtMost(1.0)
            fun channel(shift: Int, gain: Double): Int {
                val lean = 1 + (gain - 1) * shallow
                return ((tinted shr shift and 255) * lean * dark / 255).toInt().coerceIn(0, 255)
            }
            val opacity = 1 - (1 - EDGE_OPACITY) * exp(-deep / OPACITY_DEPTH)
            return ((opacity * 255).toInt() shl 24) or
                (channel(16, SHALLOW_RED) shl 16) or
                (channel(8, SHALLOW_GREEN) shl 8) or
                channel(0, SHALLOW_BLUE)
        }

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
