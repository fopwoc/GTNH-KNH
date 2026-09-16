package io.github.fopwoc.mods.framework.ui.compose.canvas

/** Ordered image commands. Images in one frame share dimensions for one texture-array batch. */
class GpuCanvasFrame(draws: List<GpuImageDraw>) {
  val draws: List<GpuImageDraw> = draws.toList()

  init {
    val first = this.draws.firstOrNull()?.image
    if (first != null) {
      require(this.draws.all { it.image.width == first.width && it.image.height == first.height }) {
        "Images in one GPU canvas frame must have equal dimensions"
      }
    }
  }
}
