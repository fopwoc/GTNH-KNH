package io.github.fopwoc.mods.palimpsest.client.minimap

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MinimapMarksTest {
    @Test
    fun allMarkerKindsUseTheSameFractionalCameraCenter() {
        val marks = MinimapMarks(MapCamera(8.5, 8.5, 1.0, 100, 100), null)
        val waypoint = Waypoint(UUID.randomUUID(), "Home", 8, 64, 8, ItemId("minecraft:stone"))
        val ore = ProspectingMark(ProspectingMark.Kind.ORE, 8, 64, 8, "Ore")
        val claim = ClaimMark(0, 0, "Team", 0xFFFFFF, false)

        assertEquals(50.0 to 50.0, marks.waypoints(listOf(waypoint)).single().let { it.x to it.y })
        assertEquals(
            MapMark(50, 50),
            marks.prospecting(listOf(ore), ProspectingLayers.Enabled(ore = true)).single().at,
        )
        assertEquals(50 to 50, marks.claims(listOf(claim)).single().let { it.x to it.y })
    }
}
