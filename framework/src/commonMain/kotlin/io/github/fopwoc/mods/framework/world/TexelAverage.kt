package io.github.fopwoc.mods.framework.world

import kotlin.math.pow

/**
 * The colour a texture reads as from afar, for maps. Averaged in linear light: a dark texture with
 * a few bright lines then reads lighter than a flat dark one, the way the eye sees it, instead of
 * collapsing to the same grey. Transparent texels are skipped and translucent ones weigh by their
 * alpha, so a flower is its petals' colour and a leaf block its leaves', not a blend with nothing.
 */
object TexelAverage {
    /** Average alpha over a texture below which a block is see-through: torches, string. */
    const val OPAQUE_ALPHA = 10

    /**
     * One texture as a layer: its colour over opaque texels and how much of it is opaque (0..255).
     */
    class Layer(val argb: Int, val coverage: Int)

    /** The [width] × [height] texels read through [argbAt]; null for an empty texture. */
    inline fun layer(width: Int, height: Int, argbAt: (x: Int, y: Int) -> Int): Layer? {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var alpha = 0L
        var weight = 0.0
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = argbAt(x, y)
            val a = pixel ushr 24
            alpha += a
            if (a == 0) continue
            val w = a / 255.0
            weight += w
            r += toLinear(pixel shr 16 and 255) * w
            g += toLinear(pixel shr 8 and 255) * w
            b += toLinear(pixel and 255) * w
        }
        val texels = width * height
        if (texels == 0) return null
        if (weight == 0.0) return Layer(0, 0)
        val argb =
            (0xFF shl 24) or
                (toSrgb(r / weight) shl 16) or
                (toSrgb(g / weight) shl 8) or
                toSrgb(b / weight)
        return Layer(argb, (alpha / texels).toInt())
    }

    /**
     * Composites layers bottom-up by coverage, the way the renderer stacks them; null when nothing
     * shows.
     */
    fun compose(layers: List<Layer>): Int? {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var alpha = 0.0
        for (layer in layers) {
            val a = layer.coverage / 255.0
            if (a <= 0.0) continue
            r = r * (1 - a) + (layer.argb shr 16 and 255) * a
            g = g * (1 - a) + (layer.argb shr 8 and 255) * a
            b = b * (1 - a) + (layer.argb and 255) * a
            alpha += a * (1 - alpha)
        }
        if (alpha * 255 < OPAQUE_ALPHA) return null
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /** Multiplies an ARGB colour by an RGB tint, keeping alpha. */
    fun multiply(argb: Int, rgb: Int): Int {
        if (rgb and WHITE == WHITE) return argb
        val r = (argb shr 16 and 255) * (rgb shr 16 and 255) / 255
        val g = (argb shr 8 and 255) * (rgb shr 8 and 255) / 255
        val b = (argb and 255) * (rgb and 255) / 255
        return (argb and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
    }

    @PublishedApi internal fun toLinear(value: Int): Double = TO_LINEAR[value]

    @PublishedApi
    internal fun toSrgb(linear: Double): Int {
        val c = if (linear <= 0.0031308) linear * 12.92 else 1.055 * linear.pow(1 / 2.4) - 0.055
        return (c * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    private const val WHITE = 0xFFFFFF

    private val TO_LINEAR =
        DoubleArray(256) { value ->
            val c = value / 255.0
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
}
