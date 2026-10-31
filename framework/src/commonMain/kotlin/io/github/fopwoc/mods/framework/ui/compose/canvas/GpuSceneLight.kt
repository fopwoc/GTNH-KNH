package io.github.fopwoc.mods.framework.ui.compose.canvas

import kotlin.math.max
import kotlin.math.sqrt

/**
 * One distant light over a [GpuScene]: it shines from ([x], [y], [z]), y up, in [color] RGB at
 * [intensity], over [ambient] light that reaches every face alike. Shiny faces also glint where the
 * light mirrors towards the viewer.
 */
class GpuSceneLight(
    x: Double,
    y: Double,
    z: Double,
    val color: Int = WHITE,
    val intensity: Float = 1f,
    val ambient: Float = DEFAULT_AMBIENT,
) {
    private val length = sqrt(x * x + y * y + z * z)
    val x = (x / length).toFloat()
    val y = (y / length).toFloat()
    val z = (z / length).toFloat()

    init {
        require(length > 0.0) { "A light needs a direction" }
        require(intensity in 0f..1f && ambient in 0f..1f)
    }

    /**
     * How bright a face pointing [face] is under this light, 0 to 1, before its colour: ambient
     * light plus the share of the light falling on it.
     */
    fun brightness(face: GpuSceneFace): Float {
        val facing = face.x * x + face.y * y + face.z * z
        return (ambient + (1 - ambient) * intensity * max(facing, 0f)).coerceAtMost(1f)
    }

    companion object {
        private const val WHITE = 0xFFFFFF
        private const val DEFAULT_AMBIENT = 0.55f

        /** High in the north-west, the classic map light: what a scene gets unless told. */
        val FIXED = GpuSceneLight(-0.35, 1.0, -0.35)
    }
}

/**
 * Which way a scene face points, y up. A mesh colour's alpha byte carries it, with whether the face
 * is shiny; scene colours are always opaque, so the byte is free. Build such colours with
 * [GpuSceneMesh.color].
 */
enum class GpuSceneFace(val x: Int, val y: Int, val z: Int) {
    UP(0, 1, 0),
    EAST(1, 0, 0),
    WEST(-1, 0, 0),
    SOUTH(0, 0, 1),
    NORTH(0, 0, -1),
    DOWN(0, -1, 0);

    companion object {
        fun of(x: Int, y: Int, z: Int): GpuSceneFace = entries.first {
            it.x == x && it.y == y && it.z == z
        }
    }
}
