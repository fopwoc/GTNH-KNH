package io.github.fopwoc.mods.palimpsest.render.iso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnMesherTest {
    /** A low red column west of a tall blue one. */
    private val field =
        Heightfield(
            0,
            0,
            1,
            2,
            1,
            intArrayOf(64, 70),
            intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
        )

    private fun camera(yaw: Double, pitch: Double = 35.0) =
        IsoCamera(1.0, 64.0, 0.5, 8.0, yaw, pitch, 200, 200)

    private fun mesh(yaw: Double, pitch: Double = 35.0) =
        ColumnMesher.mesh(field, camera(yaw, pitch))

    @Test
    fun straightDownOnlyTopsAreDrawn() {
        assertEquals(12, mesh(0.0, IsoCamera.MAX_PITCH).vertexCount)
    }

    @Test
    fun fromTheSouthEachColumnShowsItsSouthSide() {
        // Neither has a neighbour to the south; edge-on east and west sides are skipped.
        assertEquals(24, mesh(0.0).vertexCount)
    }

    @Test
    fun aSideFacingATallerNeighbourIsSkipped() {
        // From the east the red column's east side faces the taller blue one.
        assertEquals(18, mesh(90.0).vertexCount)
        // From the west the blue column's west side rises above the red one, so both show.
        assertEquals(24, mesh(270.0).vertexCount)
    }

    @Test
    fun theFarColumnIsDrawnFirst() {
        fun firstIsBlue(yaw: Double) = mesh(yaw).colors[0] and 0xFF > 0
        assertTrue(!firstIsBlue(90.0), "from the east the western red column is far")
        assertTrue(firstIsBlue(270.0), "from the west the eastern blue column is far")
    }

    @Test
    fun columnsOffTheCanvasAreLeftOut() {
        val far = IsoCamera(10_000.0, 64.0, 0.0, 8.0, 0.0, 35.0, 200, 200)
        assertEquals(0, ColumnMesher.mesh(field, far).vertexCount)
    }
}
