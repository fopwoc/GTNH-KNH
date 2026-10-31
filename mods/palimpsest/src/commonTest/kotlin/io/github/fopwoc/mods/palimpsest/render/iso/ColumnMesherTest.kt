package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneFace
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        ColumnMesher.mesh(field, ColumnMesher.floor(field), column0, 0, columns, 1).solid

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

    @Test
    fun waterLiesOverItsFloorAndThinsTowardsTheShore() {
        // Dry land, then water three blocks deep over floors at 61.
        val tops = intArrayOf(66, 64, 64, 64)
        val depths = intArrayOf(0, 3, 3, 3)
        val field =
            Heightfield(
                0,
                0,
                1,
                tops.size,
                1,
                tops,
                IntArray(tops.size) { 0xFF336699.toInt() },
                depths,
                IntArray(tops.size) { if (depths[it] > 0) 0xFF3060E0.toInt() else 0 },
            )
        val meshes = ColumnMesher.mesh(field, ColumnMesher.floor(field), 0, 0, tops.size, 1)
        val solidTops =
            (0 until meshes.solid.quadCount)
                .map { quad -> (0 until 4).map { meshes.solid.y(quad * 4 + it) } }
                .filter { it.distinct().size == 1 }
                .map { it[0] }
        assertEquals(listOf(66f, 61f, 61f, 61f), solidTops)
        val water = assertNotNull(meshes.water)
        assertTrue(water.translucent)
        // The surface's corners at x 1 touch the shore; those at x 3 lie over open water.
        fun surface(x: Float) =
            (0 until water.vertexCount).filter {
                water.x(it) == x && GpuSceneMesh.face(water.colors[it]) == GpuSceneFace.UP
            }
        val shore = surface(1f)
        val open = surface(3f)
        val opacity = { index: Int -> GpuSceneMesh.opacity(water.colors[index]) }
        assertTrue(shore.maxOf(opacity) < open.minOf(opacity), "the shore is thinner")
    }
}
