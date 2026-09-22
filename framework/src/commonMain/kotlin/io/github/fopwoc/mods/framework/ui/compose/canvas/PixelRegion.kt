package io.github.fopwoc.mods.framework.ui.compose.canvas

/** Pixel coordinates in a canvas, with an exclusive right and bottom edge. */
data class PixelRegion(val left: Int, val top: Int, val width: Int, val height: Int) {
    init {
        require(left >= 0 && top >= 0)
        require(width > 0 && height > 0)
        require(left.toLong() + width <= Int.MAX_VALUE)
        require(top.toLong() + height <= Int.MAX_VALUE)
    }

    val right: Int
        get() = left + width

    val bottom: Int
        get() = top + height
}
