package io.github.fopwoc.mods.testgui.client.gui.ui.story

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage

internal class NoiseImages {
    private val cache = LinkedHashMap<Triple<Int, Int, Int>, GpuImage>(128, 0.75f, true)

    fun image(level: Int, x: Int, y: Int): GpuImage =
        cache.getOrPut(Triple(level, x, y)) {
            if (cache.size >= 512) cache.remove(cache.entries.first().key)
            val bytes = ByteArray(32 * 32 * 4)
            val tint = hash(x, y, level)
            for (py in 0 until 32) for (px in 0 until 32) {
                val at = (py * 32 + px) * 4
                val grain = hash(x * 32 + px, y * 32 + py, level) and 31
                val border = px == 0 || py == 0
                bytes[at] = (if (border) 25 else (tint and 63) + 55 + grain).toByte()
                bytes[at + 1] = (if (border) 28 else ((tint ushr 8) and 63) + 75 + grain).toByte()
                bytes[at + 2] = (if (border) 38 else ((tint ushr 16) and 63) + 95 + grain).toByte()
                bytes[at + 3] = 0xFF.toByte()
            }
            GpuImage(32, 32, bytes)
        }

    private fun hash(x: Int, y: Int, level: Int): Int {
        var value = x * 0x1f123bb5 + y * 0x5f356495 + level * 0x4cf5ad43
        value = (value xor (value ushr 16)) * 0x7feb352d
        return (value xor (value ushr 15)) * 0x846ca68b.toInt()
    }
}
