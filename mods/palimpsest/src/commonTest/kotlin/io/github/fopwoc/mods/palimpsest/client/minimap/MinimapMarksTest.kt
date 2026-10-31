package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.mods.palimpsest.render.iso.MarkProjection
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimapMarksTest {
    private fun marks(yaw: Double) =
        MinimapMarks(
            MarkProjection(IsoCamera(8.5, 64.0, 8.5, 1.0, yaw, IsoCamera.MAX_PITCH, 100, 100)) {
                _,
                _ ->
                64.0
            }
        )

    @Test
    fun allMarkerKindsUseTheSameFractionalCameraCenter() {
        val marks = marks(0.0)
        val waypoint = Waypoint(UUID.randomUUID(), "Home", 8, 64, 8, ItemId("minecraft:stone"))
        val ore = ProspectingMark(ProspectingMark.Kind.ORE, 8, 64, 8, "Ore")
        val claim = ClaimMark(0, 0, "Team", 0xFFFFFF, false)

        val (x, y) = marks.waypoints(listOf(waypoint)).single().let { it.x to it.y }
        assertEquals(50.0, x, 1e-6)
        assertEquals(50.0, y, 1e-6)
        assertEquals(
            MapMark(50, 50),
            marks.prospecting(listOf(ore), ProspectingLayers.Enabled(ore = true)).single().at,
        )
        assertEquals(50 to 50, marks.claims(listOf(claim)).single().let { it.x to it.y })
    }

    @Test
    fun aTurnedMinimapPutsTheSameMarkWhereTheTurnSays() {
        // Looking from the east, east is down: a mark ten blocks east sits under the centre.
        val east = Waypoint(UUID.randomUUID(), "East", 18, 64, 8, ItemId("minecraft:stone"))
        val (x, y) = marks(90.0).waypoints(listOf(east)).single().let { it.x to it.y }
        assertEquals(50.0, x, 1e-6)
        assertEquals(60.0, y, 1e-6)
    }
}
