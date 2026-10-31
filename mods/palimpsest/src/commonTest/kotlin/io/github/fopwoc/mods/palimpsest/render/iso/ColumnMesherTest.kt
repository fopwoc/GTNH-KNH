package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnMesherTest {
    private fun field(vararg tops: Int) =
        Heightfield(
            32,
            -16,
            1,
            tops.size,
            1,
            tops,
            IntArray(tops.size) { if (tops[it] == 0) 0 else 0xFF336699.toInt() },
        )

    private fun mesh(field: Heightfield, column0: Int = 0, columns: Int = field.columns) =
        ColumnMesher.mesh(field, ColumnMesher.floor(field), column0, 0, columns, 1)

    /** Vertex heights of each quad that is not a top, as low to high. */
    private fun GpuSceneMesh.sides(): List<Pair<Float, Float>> =
        (0 until quadCount)
            .map { quad -> (0 until 4).map { y(quad * 4 + it) } }
            .filter { it.distinct().size > 1 }
            .map { it.min() to it.max() }

    @Test
    fun aLoneColumnStandsOnTheFloorOnAllFourSides() {
        val mesh = mesh(field(70))
        assertEquals(5, mesh.quadCount)
        assertEquals(List(4) { 64f to 70f }, mesh.sides())
    }

    @Test
    fun sidesOnlyRiseAboveTheNeighbour() {
        // The low column's east side faces the taller one; the tall one's west side drops to it.
        val sides = mesh(field(64, 70)).sides()
        assertEquals(3, sides.count { it == 58f to 64f })
        assertEquals(1, sides.count { it == 64f to 70f })
        assertEquals(3, sides.count { it == 58f to 70f })
        assertEquals(7, sides.size)
    }

    @Test
    fun unseenColumnsAreGapsDownToTheFloor() {
        val mesh = mesh(field(70, 0, 70))
        assertEquals(2 * 5, mesh.quadCount)
    }

    @Test
    fun positionsAreRelativeToTheRegion() {
        val mesh = mesh(field(70, 72), column0 = 1, columns = 1)
        assertEquals(33.0, mesh.originX)
        assertEquals(-16.0, mesh.originZ)
        assertEquals(0f, (0 until mesh.vertexCount).minOf { mesh.x(it) })
    }
}
