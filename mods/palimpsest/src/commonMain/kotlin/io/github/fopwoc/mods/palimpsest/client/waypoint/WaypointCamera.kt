package io.github.fopwoc.mods.palimpsest.client.waypoint

/** First-person eye and view angles in Minecraft's yaw and pitch convention. */
data class WaypointCamera(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val verticalFov: Double,
)
