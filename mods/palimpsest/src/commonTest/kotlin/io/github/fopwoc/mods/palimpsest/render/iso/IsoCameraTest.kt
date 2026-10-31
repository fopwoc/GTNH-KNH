package io.github.fopwoc.mods.palimpsest.render.iso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IsoCameraTest {
    private fun camera(yaw: Double, pitch: Double) =
        IsoCamera(100.0, 64.0, -40.0, 2.0, yaw, pitch, 320, 200)

    @Test
    fun straightDownFromTheSouthIsTheFlatMap() {
        val camera = camera(0.0, IsoCamera.MAX_PITCH)
        assertEquals(160.0 + 10 * 2.0, camera.screenX(110.0, -40.0), 1e-9)
        assertEquals(100.0 + 5 * 2.0, camera.screenY(100.0, 90.0, -35.0), 1e-9)
    }

    @Test
    fun theViewerSideIsNearerAndHigherGroundStandsUp() {
        val fromEast = camera(90.0, 30.0)
        // Seen from the east, the east is lower on the screen and north is to the right.
        assertTrue(fromEast.screenY(110.0, 64.0, -40.0) > fromEast.screenY(90.0, 64.0, -40.0))
        assertTrue(fromEast.screenX(100.0, -50.0) > fromEast.screenX(100.0, -30.0))
        assertTrue(fromEast.screenY(100.0, 80.0, -40.0) < fromEast.screenY(100.0, 64.0, -40.0))
    }

    @Test
    fun groundAtUndoesTheProjectionAtAnyAngle() {
        for (yaw in listOf(0.0, 37.0, 135.0, 270.0)) for (pitch in listOf(15.0, 35.264, 90.0)) {
            val camera = camera(yaw, pitch)
            val (x, z) =
                camera.groundAt(
                    camera.screenX(123.0, -17.0),
                    camera.screenY(123.0, 70.0, -17.0),
                    70.0,
                )
            assertEquals(123.0, x, 1e-6, "yaw $yaw pitch $pitch")
            assertEquals(-17.0, z, 1e-6, "yaw $yaw pitch $pitch")
        }
    }

    /** Where the scene camera puts a point: its rows relative to the point keep only the offset. */
    private fun IsoCamera.scene(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val rows = sceneCamera().relativeTo(x, y, z)
        return Triple(rows[3].toDouble(), rows[7].toDouble(), rows[11].toDouble())
    }

    @Test
    fun theSceneCameraProjectsLikeTheScreen() {
        for (yaw in listOf(0.0, 37.0, 135.0, 270.0)) for (pitch in listOf(15.0, 35.264, 90.0)) {
            val camera = camera(yaw, pitch)
            val (x, y, _) = camera.scene(130.0, 80.0, -10.0)
            assertEquals(camera.screenX(130.0, -10.0), x, 1e-3, "x at $yaw/$pitch")
            assertEquals(camera.screenY(130.0, 80.0, -10.0), y, 1e-3, "y at $yaw/$pitch")
        }
    }

    @Test
    fun sceneDepthIsSmallerTowardsTheViewerAndWithinRange() {
        val fromSouth = camera(0.0, 30.0)
        // The south and higher ground are nearer the viewer.
        assertTrue(
            fromSouth.scene(100.0, 64.0, -30.0).third < fromSouth.scene(100.0, 64.0, -50.0).third
        )
        assertTrue(
            fromSouth.scene(100.0, 90.0, -40.0).third < fromSouth.scene(100.0, 64.0, -40.0).third
        )
        for (pitch in listOf(15.0, 90.0)) {
            val camera = camera(0.0, pitch)
            // The bottom corner of the canvas at the deepest floor still fits in the depth range.
            val (x, z) = camera.groundAt(0.0, 200.0, 64.0 - 400)
            assertTrue(camera.scene(x, 64.0 - 400, z).third in -1.0..1.0, "at $pitch")
        }
    }
}
