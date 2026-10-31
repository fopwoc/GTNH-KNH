package io.github.fopwoc.mods.framework.ui.compose.canvas

/**
 * World-space [meshes] seen through [camera] with a depth test, so they need no order, lit by
 * [light] and faded to [alpha] as a whole. A renderer uploads each mesh the first frame it appears
 * and frees it after the first frame it does not.
 *
 * The more the camera looks straight down, the more a renderer that lights in a shader leans on the
 * fixed relief light ([GpuSceneLight.relief]) and on height: ground above [level] lightens and
 * warms, ground below it darkens and cools, so a snowfield seen from above still shows its hills.
 * No [level], no height tint.
 */
class GpuScene(
    meshes: List<GpuSceneMesh>,
    val camera: GpuSceneCamera,
    val alpha: Float = 1f,
    val light: GpuSceneLight = GpuSceneLight.FIXED,
    val level: Double? = null,
) {
    val meshes: List<GpuSceneMesh> = meshes.toList()

    init {
        require(alpha in 0f..1f)
        require(level?.isFinite() != false)
    }
}
