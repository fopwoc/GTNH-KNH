package io.github.fopwoc.mods.testgui.client.gui.ui.story

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage

/** Keeps pixel generation cheap so the sample mainly exercises image copies and GPU uploads. */
internal class AnimatedCanvasImages {
    private val pixels = ByteArray(SIZE * SIZE * 4)
    private var stripeX = 0

    init {
        for (y in 0 until SIZE) for (x in 0 until SIZE) paint(x, y, false)
    }

    fun next(): GpuImage {
        for (y in 0 until SIZE) for (dx in 0 until STRIPE_WIDTH) {
            paint((stripeX + dx) % SIZE, y, false)
        }
        stripeX = (stripeX + STRIPE_WIDTH) % SIZE
        for (y in 0 until SIZE) for (dx in 0 until STRIPE_WIDTH) {
            paint((stripeX + dx) % SIZE, y, true)
        }
        return GpuImage(SIZE, SIZE, pixels)
    }

    private fun paint(x: Int, y: Int, stripe: Boolean) {
        val at = (y * SIZE + x) * 4
        val grain = (x * 17 xor y * 31) and 31
        pixels[at] = (if (stripe) 245 else 45 + grain).toByte()
        pixels[at + 1] = (if (stripe) 195 else 85 + grain).toByte()
        pixels[at + 2] = (if (stripe) 70 else 140 + grain).toByte()
        pixels[at + 3] = 0xFF.toByte()
    }

    companion object {
        const val SIZE = 512
        private const val STRIPE_WIDTH = 16
    }
}
