package io.github.fopwoc.mods.framework.ui.compose.canvas

/** One image quad, positioned in the canvas's local GUI coordinates. */
data class GpuImageDraw(
    val image: GpuImage,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
  init {
    require(x.isFinite() && y.isFinite())
    require(width.isFinite() && width > 0f && height.isFinite() && height > 0f)
  }
}
