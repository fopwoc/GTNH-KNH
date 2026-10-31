package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * Ordered image commands over an optional [mesh], itself over an optional [scene], drawn bottom up.
 * Images in one frame share dimensions for one texture-array batch.
 */
class GpuCanvasFrame(
    draws: List<GpuImageDraw>,
    val mesh: GpuMesh = GpuMesh.EMPTY,
    val scene: GpuScene? = null,
) {
    val draws: List<GpuImageDraw> = draws.toList()

    val isEmpty: Boolean
        get() = this.draws.isEmpty() && mesh.vertexCount == 0 && scene?.meshes.isNullOrEmpty()

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
