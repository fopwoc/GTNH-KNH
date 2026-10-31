package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import kotlin.math.roundToInt

/** Places every kind of map marker on the minimap through its one [projection]. */
internal class MinimapMarks(private val projection: MarkProjection) {
    fun waypoints(waypoints: List<Waypoint>): List<MinimapWaypoint> =
        waypoints.mapNotNull { waypoint ->
            val (x, y) = projection.at(waypoint.x + 0.5, waypoint.y.toDouble(), waypoint.z + 0.5)
            if (!inside(x, y)) null else MinimapWaypoint(x, y, waypoint.icon, waypoint.name)
        }

    fun prospecting(
        marks: List<ProspectingMark>,
        enabled: ProspectingLayers.Enabled,
    ): List<MinimapProspectingMark> = marks.mapNotNull { mark ->
        if (!enabled.shows(mark)) return@mapNotNull null
        val (screenX, screenY) =
            projection.at(mark.x.toDouble(), mark.y?.toDouble(), mark.z.toDouble())
        val x = screenX.roundToInt()
        val y = screenY.roundToInt()
        if (!inside(x.toDouble(), y.toDouble())) null
        else MinimapProspectingMark(MapMark(x, y), mark)
    }

    fun claims(claims: List<ClaimMark>): List<MinimapClaim> = claims.mapNotNull { claim ->
        val (screenX, screenY) =
            projection.at(
                (claim.chunkX + 0.5) * CHUNK_SIDE,
                null,
                (claim.chunkZ + 0.5) * CHUNK_SIDE,
            )
        val x = screenX.roundToInt()
        val y = screenY.roundToInt()
        if (!inside(x.toDouble(), y.toDouble())) null else MinimapClaim(x, y, claim)
    }

    private fun inside(x: Double, y: Double) =
        x >= 0.0 && x < projection.camera.width && y >= 0.0 && y < projection.camera.height

    private companion object {
        const val CHUNK_SIDE = 16.0
    }
}
