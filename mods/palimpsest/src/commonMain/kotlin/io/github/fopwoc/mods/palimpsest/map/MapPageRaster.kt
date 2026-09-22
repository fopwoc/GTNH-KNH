package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage

/** A derived page with stable image identity for the GPU residency cache. */
class MapPageRaster internal constructor(private val rgba: ByteArray) {
    val image: GpuImage by lazy { GpuImage(MapPageKey.SIDE, MapPageKey.SIDE, rgba) }

    fun colorAt(x: Int, z: Int): Int {
        require(x in 0 until MapPageKey.SIDE && z in 0 until MapPageKey.SIDE)
        val at = (z * MapPageKey.SIDE + x) * 4
        return ((rgba[at + 3].toInt() and 255) shl 24) or
            ((rgba[at].toInt() and 255) shl 16) or
            ((rgba[at + 1].toInt() and 255) shl 8) or
            (rgba[at + 2].toInt() and 255)
    }

    internal fun component(at: Int, offset: Int): Int = rgba[at + offset].toInt() and 255

    internal fun copyPixels(): ByteArray = rgba.copyOf()
}
