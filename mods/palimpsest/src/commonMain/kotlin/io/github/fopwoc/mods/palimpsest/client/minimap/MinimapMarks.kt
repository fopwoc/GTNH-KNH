package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import kotlin.math.roundToInt

/** Projects all map markers through the same minimap camera and optional turn. */
internal class MinimapMarks(private val camera: MapCamera, private val turn: MapTurn?) {
    fun waypoints(waypoints: List<Waypoint>): List<MinimapWaypoint> =
        waypoints.mapNotNull { waypoint ->
            val (x, y) = at(waypoint.x + 0.5, waypoint.z + 0.5)
            if (!inside(x, y)) null else MinimapWaypoint(x, y, waypoint.icon, waypoint.name)
        }

    fun prospecting(
        marks: List<ProspectingMark>,
        enabled: ProspectingLayers.Enabled,
    ): List<MinimapProspectingMark> = marks.mapNotNull { mark ->
        if (!enabled.shows(mark)) return@mapNotNull null
        val (screenX, screenY) = at(mark.x.toDouble(), mark.z.toDouble())
        val x = screenX.roundToInt()
        val y = screenY.roundToInt()
        if (!inside(x, y)) null else MinimapProspectingMark(MapMark(x, y), mark)
    }

    fun claims(claims: List<ClaimMark>): List<MinimapClaim> = claims.mapNotNull { claim ->
        val (screenX, screenY) =
            at((claim.chunkX + 0.5) * CHUNK_SIDE, (claim.chunkZ + 0.5) * CHUNK_SIDE)
        val x = screenX.roundToInt()
        val y = screenY.roundToInt()
        if (!inside(x, y)) null else MinimapClaim(x, y, claim)
    }

    private fun at(worldX: Double, worldZ: Double): Pair<Double, Double> {
        val dx = (worldX - camera.centerX) * camera.pixelsPerBlock
        val dz = (worldZ - camera.centerZ) * camera.pixelsPerBlock
        return camera.width / 2.0 + (turn?.x(dx, dz) ?: dx) to
            camera.height / 2.0 + (turn?.y(dx, dz) ?: dz)
    }

    private fun inside(x: Double, y: Double) =
        x >= 0.0 && x < camera.width && y >= 0.0 && y < camera.height

    private fun inside(x: Int, y: Int) = x in 0 until camera.width && y in 0 until camera.height

    private companion object {
        const val CHUNK_SIDE = 16.0
    }
}
