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
}
