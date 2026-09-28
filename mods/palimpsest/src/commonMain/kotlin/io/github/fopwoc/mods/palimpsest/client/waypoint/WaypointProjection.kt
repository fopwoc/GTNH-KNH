package io.github.fopwoc.mods.palimpsest.client.waypoint

import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import kotlin.math.abs
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
    ): WaypointHudMark? {
        if (width <= 0 || height <= 0 || camera.verticalFov !in 1.0..179.0) return null
        val dx = waypoint.x + 0.5 - camera.x
        val dy = waypoint.y + 0.5 - camera.y
        val dz = waypoint.z + 0.5 - camera.z
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
            return WaypointHudMark(
                waypoint,
                projectedX.roundToInt(),
                projectedY.roundToInt(),
                distance.roundToInt(),
                false,
            )
        }

        var directionX = if (shown) projectedX - centerX else right
        var directionY = if (shown) projectedY - centerY else -up
        if (abs(directionX) + abs(directionY) < MIN_DEPTH) directionY = centerY
        val factor =
            min(
                (centerX - insetX) / abs(directionX).coerceAtLeast(MIN_DEPTH),
                (centerY - insetY) / abs(directionY).coerceAtLeast(MIN_DEPTH),
            )
        return WaypointHudMark(
            waypoint,
            (centerX + directionX * factor).roundToInt(),
            (centerY + directionY * factor).roundToInt(),
            distance.roundToInt(),
            true,
        )
    }

    private const val EDGE_INSET_X = 56.0
    private const val EDGE_INSET_Y = 28.0
    private const val MIN_DISTANCE = 3.0
    private const val MIN_DEPTH = 1e-6
}

internal data class WaypointHudMark(
    val waypoint: Waypoint,
    val x: Int,
    val y: Int,
    val distance: Int,
    val atEdge: Boolean,
)
