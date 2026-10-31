package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * World-space [meshes] seen through [camera] with a depth test, so they need no order. A renderer
 * uploads each mesh the first frame it appears and frees it after the first frame it does not.
 */
class GpuScene(meshes: List<GpuSceneMesh>, val camera: GpuSceneCamera) {
    val meshes: List<GpuSceneMesh> = meshes.toList()
}
