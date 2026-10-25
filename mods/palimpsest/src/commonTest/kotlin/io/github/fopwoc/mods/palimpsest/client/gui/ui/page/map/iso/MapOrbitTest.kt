package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapOrbitTest {
    private fun MapOrbit.arrive() {
        repeat(1000) { advance(0.05) }
    }

    @Test
    fun startsAsTheFlatMap() {
        assertTrue(MapOrbit().isFlat)
    }

    @Test
    fun turnsTheShortWayAcrossNorth() {
        val orbit = MapOrbit()
        orbit.lookFrom(350.0, 40.0)
        orbit.arrive()
        assertEquals(-10.0, orbit.yaw, 1e-9)
        assertEquals(350.0, orbit.heading, 1e-9)
        assertFalse(orbit.isFlat)
    }

    @Test
    fun quarterTurnsTiltOffTopDownAndSquareUp() {
        val orbit = MapOrbit()
        orbit.dragBy(10.0, 0.0)
        orbit.turnQuarters(1)
        orbit.arrive()
        assertEquals(90.0, orbit.yaw, 1e-9)
        assertEquals(MapOrbit.ISOMETRIC_PITCH, orbit.pitch, 1e-9)
    }

    @Test
    fun aDragEndingNearlyFlatSettlesFlat() {
        val orbit = MapOrbit()
        orbit.dragBy(3.0, -2.0)
        orbit.settle()
        orbit.arrive()
        assertTrue(orbit.isFlat)

        orbit.dragBy(20.0, -30.0)
        orbit.settle()
        orbit.arrive()
        assertEquals(20.0, orbit.yaw, 1e-9)
        assertEquals(60.0, orbit.pitch, 1e-9)
    }

    @Test
    fun aCutIsNeverFlatAndPitchStaysInRange() {
        val orbit = MapOrbit()
        orbit.cutAt(40)
        assertFalse(orbit.isFlat)
        orbit.dragBy(0.0, -500.0)
        assertEquals(IsoCamera.MIN_PITCH, orbit.pitch)
    }

    @Test
    fun aFlatDragMovesTheGroundUnderTheCursor() {
        val orbit = MapOrbit()
        orbit.lookFrom(90.0, 30.0)
        orbit.arrive()
        // From the east, dragging right moves the map north and dragging down moves it east.
        val (rightX, rightZ) = orbit.toFlat(10.0, 0.0)
        assertEquals(0.0, rightX, 1e-9)
        assertEquals(-10.0, rightZ, 1e-9)
        val (downX, downZ) = orbit.toFlat(0.0, 5.0)
        assertEquals(10.0, downX, 1e-9)
        assertEquals(0.0, downZ, 1e-9)
    }
}
