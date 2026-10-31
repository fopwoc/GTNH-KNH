package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneCamera
import kotlin.math.cos
import kotlin.math.sin

/**
 * An orthographic view of the world from the compass direction [yaw] — 0 from the south, so north
 * is up, 90 from the east — tilted down by [pitch]: 90 looks straight down and matches the flat
 * map, lower looks across the ground. The point ([centerX], [centerY], [centerZ]) sits in the
 * middle of a [width] × [height] canvas, [pixelsPerBlock] across the ground.
 */
class IsoCamera(
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val pixelsPerBlock: Double,
    val yaw: Double,
    val pitch: Double,
    val width: Int,
    val height: Int,
) {
    init {
        require(pixelsPerBlock.isFinite() && pixelsPerBlock > 0)
        require(pitch in MIN_PITCH..MAX_PITCH) { "Pitch $pitch out of range" }
        require(width > 0 && height > 0)
    }

    private val cosYaw = cos(Math.toRadians(yaw))
    private val sinYaw = sin(Math.toRadians(yaw))

    /** Ground direction towards the viewer: nearer is lower on the screen. */
    val towardX: Double
        get() = sinYaw

    val towardZ: Double
        get() = cosYaw

    /** Screen pixels per block of depth, and per block of height. */
    val depthScale = sin(Math.toRadians(pitch)) * pixelsPerBlock
    val heightScale = cos(Math.toRadians(pitch)) * pixelsPerBlock

    fun screenX(x: Double, z: Double): Double =
        width / 2.0 + ((x - centerX) * cosYaw - (z - centerZ) * sinYaw) * pixelsPerBlock

    fun screenY(x: Double, y: Double, z: Double): Double =
        height / 2.0 + ((x - centerX) * sinYaw + (z - centerZ) * cosYaw) * depthScale -
            (y - centerY) * heightScale

    /** The ground point at height [y] that lands on screen point ([sx], [sy]), as x to z. */
    fun groundAt(sx: Double, sy: Double, y: Double): Pair<Double, Double> {
        val across = (sx - width / 2.0) / pixelsPerBlock
        val depth = (sy - height / 2.0 + (y - centerY) * heightScale) / depthScale
        return centerX + across * cosYaw + depth * sinYaw to
            centerZ - across * sinYaw + depth * cosYaw
    }

    /**
     * The same projection for the GPU, with depth along the view: nearer to the viewer is lower.
     * The depth range covers every point on the canvas within [SCENE_Y_SPAN] of [centerY].
     */
    fun sceneCamera(): GpuSceneCamera {
        val cosPitch = cos(Math.toRadians(pitch))
        val sinPitch = sin(Math.toRadians(pitch))
        val reach =
            (height / 2.0 / pixelsPerBlock + SCENE_Y_SPAN * cosPitch) / sinPitch * cosPitch +
                SCENE_Y_SPAN * sinPitch
        val range = reach * SCENE_DEPTH_MARGIN
        return GpuSceneCamera(
            doubleArrayOf(
                cosYaw * pixelsPerBlock,
                0.0,
                -sinYaw * pixelsPerBlock,
                width / 2.0 - (cosYaw * centerX - sinYaw * centerZ) * pixelsPerBlock,
                sinYaw * depthScale,
                -heightScale,
                cosYaw * depthScale,
                height / 2.0 - (sinYaw * centerX + cosYaw * centerZ) * depthScale +
                    centerY * heightScale,
                -sinYaw * cosPitch / range,
                -sinPitch / range,
                -cosYaw * cosPitch / range,
                (sinYaw * cosPitch * centerX + sinPitch * centerY + cosYaw * cosPitch * centerZ) /
                    range,
            )
        )
    }

    companion object {
        /** Lower than this the ground thins to a line and dragging across it means nothing. */
        const val MIN_PITCH = 15.0
        const val MAX_PITCH = 90.0
        /** How much the ground is foreshortened at most, sin([MIN_PITCH]). */
        const val MIN_PITCH_SINE = 0.2588
        /** How far above and below the centre the scene's depth range reaches, in blocks. */
        private const val SCENE_Y_SPAN = 512.0
        private const val SCENE_DEPTH_MARGIN = 1.1
    }
}
