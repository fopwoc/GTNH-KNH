package io.github.fopwoc.mods.framework.world

/** The 256 ARGB colors a [SliceScanner] byte can mean: 64 base map colors × 4 vanilla shades. */
object MapPalette {
    private val SHADE_FACTORS = intArrayOf(180, 220, 255, 135)

    /** [baseColors] are the 64 vanilla map colors as 0xRRGGBB; index 0 is transparent. */
    fun build(baseColors: IntArray): IntArray {
        require(baseColors.size == ChunkColumns.COLOR_INDEXES)
        return IntArray(ChunkColumns.COLOR_INDEXES * 4) { entry ->
            val base = baseColors[entry shr 2]
            if (entry shr 2 == ChunkColumns.TRANSPARENT) 0
            else {
                val factor = SHADE_FACTORS[entry and 3]
                val r = (base shr 16 and 255) * factor / 255
                val g = (base shr 8 and 255) * factor / 255
                val b = (base and 255) * factor / 255
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
