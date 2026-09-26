package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * One image quad, positioned in the canvas's local GUI coordinates. [rotation] turns it clockwise,
 * in degrees, around its own centre; [alpha] fades it, from 0 (invisible) to 1.
 */
data class GpuImageDraw(
    val image: GpuImage,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val alpha: Float = 1f,
) {
    init {
        require(x.isFinite() && y.isFinite())
        require(width.isFinite() && width > 0f && height.isFinite() && height > 0f)
        require(rotation.isFinite())
        require(alpha in 0f..1f)
    }
}
