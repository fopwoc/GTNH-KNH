package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Ordered image commands over an optional [mesh] drawn beneath them. Images in one frame share
 * dimensions for one texture-array batch.
 */
class GpuCanvasFrame(draws: List<GpuImageDraw>, val mesh: GpuMesh = GpuMesh.EMPTY) {
    val draws: List<GpuImageDraw> = draws.toList()

    init {
        val first = this.draws.firstOrNull()?.image
        if (first != null) {
            require(
                this.draws.all { it.image.width == first.width && it.image.height == first.height }
            ) {
                "Images in one GPU canvas frame must have equal dimensions"
            }
        }
    }
}
