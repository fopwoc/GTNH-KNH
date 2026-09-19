package io.github.fopwoc.mods.palimpsest.map

/**
 * Turns one pixel's channel values into ARGB. [values] holds one entry per channel, within that
 * channel's width, or -1 when the channel has nothing for the pixel; channel 0 decides presence. Return 0
 * (alpha 0) for a pixel the map should leave transparent.
 */
fun interface PixelShader {
    fun argb(values: IntArray): Int

    companion object {
        /** Single channel through a 256-entry palette; entry 0 is transparent, the rest opaque. */
        fun palette(palette: IntArray): PixelShader {
            require(palette.size == 256)
            val colors = IntArray(256) { if (it == 0) 0 else palette[it] or (0xFF shl 24) }
            return PixelShader { values -> colors[values[0]] }
        }
    }
}
