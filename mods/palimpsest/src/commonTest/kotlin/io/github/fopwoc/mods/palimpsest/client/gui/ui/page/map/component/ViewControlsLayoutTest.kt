package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewControlsLayoutTest {
    private val range = -64..320

    @Test
    fun theRailsTopShowsEverythingAndItsBottomTheFloor() {
        assertNull(ViewControlsLayout.sliceAt(-5.0, range))
        assertNull(ViewControlsLayout.sliceAt(0.0, range))
        assertEquals(-64, ViewControlsLayout.sliceAt(ViewControlsLayout.RAIL_HEIGHT + 9.0, range))
    }

    @Test
    fun aSliceLandsWhereTheRailShowsIt() {
        for (slice in listOf(-64, 0, 63, 200, 319)) {
            val y = ViewControlsLayout.railY(slice, range)
            val back = ViewControlsLayout.sliceAt(y.toDouble(), range)!!
            // One pixel of rail is three blocks of this range.
            assertEquals(slice.toDouble(), back.toDouble(), 3.0, "slice $slice at $y")
        }
        assertEquals(0, ViewControlsLayout.railY(null, range))
    }
}
