package io.github.fopwoc.mods.palimpsest.client.waypoint

import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaypointProjectionTest {
    private val camera = WaypointCamera(0.5, 65.5, 0.5, 0f, 0f, 70.0)

    @Test
    fun forwardPointIsCenteredAndRightPointMovesRight() {
        val ahead =
            assertNotNull(WaypointProjection.project(waypoint(0, 65, 100), camera, 400, 200))
        val right =
            assertNotNull(WaypointProjection.project(waypoint(20, 65, 100), camera, 400, 200))
        assertEquals(200, ahead.x)
        assertEquals(100, ahead.y)
        assertFalse(ahead.atEdge)
        assertTrue(right.x > ahead.x)
    }

    @Test
    fun pointBehindCameraUsesStableTurnAroundCue() {
        val behind =
            assertNotNull(WaypointProjection.project(waypoint(30, 65, -100), camera, 400, 200))
        assertTrue(behind.atEdge)
        assertEquals(EdgeDirection.BEHIND, behind.edge)
        assertEquals(200, behind.x)
        assertEquals(172, behind.y)
    }

    @Test
    fun offscreenBearingShowsWhichWayToTurn() {
        val right =
            assertNotNull(WaypointProjection.project(waypoint(100, 65, 0), camera, 400, 200))
        val left =
            assertNotNull(WaypointProjection.project(waypoint(-100, 65, 0), camera, 400, 200))
        assertEquals(EdgeDirection.RIGHT, right.edge)
        assertEquals(EdgeDirection.LEFT, left.edge)
        assertEquals(90, right.turnDegrees)
        assertEquals(90, left.turnDegrees)
        assertEquals(100, right.y)
        assertEquals(100, left.y)
    }

    @Test
    fun highTargetShowsVerticalCue() {
        val above =
            assertNotNull(WaypointProjection.project(waypoint(0, 165, 20), camera, 400, 200))
        assertEquals(EdgeDirection.UP, above.edge)
        assertEquals(200, above.x)
    }

    @Test
    fun minecraftYawNinetyFacesWest() {
        val west =
            assertNotNull(
                WaypointProjection.project(waypoint(-100, 65, 0), camera.copy(yaw = 90f), 400, 200)
            )
        assertEquals(200, west.x)
        assertFalse(west.atEdge)
    }

    private fun waypoint(x: Int, y: Int, z: Int) =
        Waypoint(UUID.randomUUID(), "Test", x, y, z, ItemId("minecraft:compass"))
}
