package io.github.fopwoc.mods.palimpsest.client.waypoint

import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Screen placement of one tracked waypoint; behind-camera points stop at the viewport edge. */
internal object WaypointProjection {
    fun project(
        waypoint: Waypoint,
        camera: WaypointCamera,
        width: Int,
        height: Int,
    ): WaypointHudMark? =
        screenPosition(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5, camera, width, height)
            ?.let { WaypointHudMark(waypoint, it.x, it.y, it.distance, it.edge, it.turnDegrees) }

    fun screenPosition(
        x: Double,
        y: Double,
        z: Double,
        camera: WaypointCamera,
        width: Int,
        height: Int,
    ): ScreenPosition? {
        if (width <= 0 || height <= 0 || camera.verticalFov !in 1.0..179.0) return null
        val dx = x - camera.x
        val dy = y - camera.y
        val dz = z - camera.z
        val distance = hypot(hypot(dx, dy), dz)
        if (distance < MIN_DISTANCE) return null

        val yaw = Math.toRadians(camera.yaw.toDouble())
        val pitch = Math.toRadians(camera.pitch.toDouble())
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)
        val sinPitch = sin(pitch)
        val cosPitch = cos(pitch)
        val right = dx * cosYaw + dz * sinYaw
        val up = -dx * sinYaw * sinPitch + dy * cosPitch + dz * cosYaw * sinPitch
        val forward = -dx * sinYaw * cosPitch - dy * sinPitch + dz * cosYaw * cosPitch
        val centerX = width / 2.0
        val centerY = height / 2.0
        val focal = centerY / tan(Math.toRadians(camera.verticalFov) / 2.0)
        val shown = forward > MIN_DEPTH
        val projectedX = if (shown) centerX + right / forward * focal else Double.NaN
        val projectedY = if (shown) centerY - up / forward * focal else Double.NaN
        val insetX = min(EDGE_INSET_X, width / 4.0)
        val insetY = min(EDGE_INSET_Y, height / 4.0)
        val inside =
            shown &&
                projectedX in insetX..(width - insetX) &&
                projectedY in insetY..(height - insetY)
        if (inside) {
            return ScreenPosition(
                projectedX.roundToInt(),
                projectedY.roundToInt(),
                distance.roundToInt(),
                null,
                0,
            )
        }

        val horizontalForward = -dx * sinYaw + dz * cosYaw
        val bearing = Math.toDegrees(atan2(right, horizontalForward))
        val turnDegrees = abs(bearing).roundToInt()
        val edge =
            when {
                turnDegrees >= BEHIND_ANGLE -> EdgeDirection.BEHIND
                shown &&
                    abs(projectedY - centerY) / (centerY - insetY) >
                        abs(projectedX - centerX) / (centerX - insetX) ->
                    if (projectedY < centerY) EdgeDirection.UP else EdgeDirection.DOWN
                bearing < 0 -> EdgeDirection.LEFT
                else -> EdgeDirection.RIGHT
            }
        return ScreenPosition(
            when (edge) {
                EdgeDirection.LEFT -> insetX.roundToInt()
                EdgeDirection.RIGHT -> (width - insetX).roundToInt()
                else -> centerX.roundToInt()
            },
            when (edge) {
                EdgeDirection.UP -> insetY.roundToInt()
                EdgeDirection.DOWN,
                EdgeDirection.BEHIND -> (height - insetY).roundToInt()
                else -> centerY.roundToInt()
            },
            distance.roundToInt(),
            edge,
            turnDegrees,
        )
    }

    private const val EDGE_INSET_X = 56.0
    private const val EDGE_INSET_Y = 28.0
    private const val MIN_DISTANCE = 3.0
    private const val MIN_DEPTH = 1e-6
    private const val BEHIND_ANGLE = 150
}

internal enum class EdgeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    BEHIND,
}

internal data class ScreenPosition(
    val x: Int,
    val y: Int,
    val distance: Int,
    val edge: EdgeDirection?,
    val turnDegrees: Int,
) {
    val atEdge: Boolean
        get() = edge != null
}

internal data class WaypointHudMark(
    val waypoint: Waypoint,
    val x: Int,
    val y: Int,
    val distance: Int,
    val edge: EdgeDirection?,
    val turnDegrees: Int,
) {
    val atEdge: Boolean
        get() = edge != null
}
