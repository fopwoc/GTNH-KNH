package io.github.fopwoc.mods.framework.ui.compose.canvas

/** Immutable RGBA8 image, with rows stored top to bottom. Reuse it until its pixels change. */
class GpuImage(val width: Int, val height: Int, rgba: ByteArray) {
  init {
    require(width > 0 && height > 0)
    require(rgba.size.toLong() == width.toLong() * height * 4)
  }

  internal val pixels = rgba.copyOf()
}
