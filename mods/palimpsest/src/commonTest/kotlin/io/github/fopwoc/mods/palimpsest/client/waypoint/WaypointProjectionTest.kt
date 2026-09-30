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
    private val camera = camera()

    @Test
    fun southFacingCameraShowsEastOnTheLeft() {
        val ahead =
            assertNotNull(WaypointProjection.project(waypoint(0, 65, 100), camera, 400, 200))
        val right =
            assertNotNull(WaypointProjection.project(waypoint(20, 65, 100), camera, 400, 200))
        assertEquals(200, ahead.x)
        assertEquals(100, ahead.y)
        assertFalse(ahead.atEdge)
        assertTrue(right.x < ahead.x)
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
            assertNotNull(WaypointProjection.project(waypoint(-100, 65, 0), camera, 400, 200))
        val left = assertNotNull(WaypointProjection.project(waypoint(100, 65, 0), camera, 400, 200))
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
                WaypointProjection.project(waypoint(-100, 65, 0), camera(west = true), 400, 200)
            )
        assertEquals(200, west.x)
        assertFalse(west.atEdge)
    }

    @Test
    fun renderedProjectionOffsetsAndLargeCoordinatesArePreserved() {
        val view = floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        val projection =
            perspective().also {
                it[8] = 0.2f
                it[9] = -0.1f
            }
        val shifted = WaypointCamera(30_000_000.5, 65.5, -30_000_000.5, view, projection)
        val position =
            assertNotNull(
                WaypointProjection.screenPosition(
                    30_000_000.5,
                    65.5,
                    -29_999_900.5,
                    shifted,
                    400,
                    200,
                )
            )
        assertEquals(160, position.x)
        assertEquals(90, position.y)
        assertFalse(position.atEdge)
    }

    @Test
    fun lookingStraightDownCentersTheBlockBelow() {
        // Rx(90) * Ry(180), as used by Minecraft's downward-facing camera.
        val view = floatArrayOf(-1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f)
        val down = WaypointCamera(0.5, 65.5, 0.5, view, perspective())
        val mark = assertNotNull(WaypointProjection.project(waypoint(0, 0, 0), down, 400, 200))
        assertEquals(200, mark.x)
        assertEquals(100, mark.y)
        assertFalse(mark.atEdge)
    }

    @Test
    fun viewTranslationAndMatrixOwnershipArePreserved() {
        val view = floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 2f, 0f, 0f, 1f)
        val translated = WaypointCamera(0.5, 65.5, 0.5, view, perspective())
        view[12] = 0f
        val mark =
            assertNotNull(WaypointProjection.project(waypoint(0, 65, 10), translated, 400, 200))
        assertEquals(229, mark.x)
        assertEquals(100, mark.y)
    }

    private fun camera(west: Boolean = false): WaypointCamera {
        // Minecraft's modelview: yaw 0 rotates 180 degrees around Y; yaw 90 rotates 270.
        val view =
            if (west) floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)
            else floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
        return WaypointCamera(0.5, 65.5, 0.5, view, perspective())
    }

    private fun perspective(): FloatArray {
        val focal = (1.0 / kotlin.math.tan(Math.toRadians(35.0))).toFloat()
        return floatArrayOf(
            focal / 2,
            0f,
            0f,
            0f,
            0f,
            focal,
            0f,
            0f,
            0f,
            0f,
            -1f,
            -1f,
            0f,
            0f,
            -0.1f,
            0f,
        )
    }

    private fun waypoint(x: Int, y: Int, z: Int) =
        Waypoint(UUID.randomUUID(), "Test", x, y, z, ItemId("minecraft:compass"))
}
