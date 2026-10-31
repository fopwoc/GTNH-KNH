package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnReliefTest {
    /** A field [columns] wide and [rows] deep with tops from [top]. */
    private fun field(columns: Int, rows: Int, top: (column: Int, row: Int) -> Int): Heightfield {
        val tops = IntArray(columns * rows) { top(it % columns, it / columns) }
        return Heightfield(0, 0, 1, columns, rows, tops, IntArray(tops.size) { 0xFF808080.toInt() })
    }

    /** The shading at the corner in the middle of an [side] × [side] field. */
    private fun middle(field: Heightfield, side: Int): Int =
        ColumnRelief(field, 0, 0, side, side).at(side / 2, side / 2)

    @Test
    fun flatGroundFacesStraightUpAndSeesTheWholeSky() {
        val shading = middle(field(16, 16) { _, _ -> 64 }, 16)
        assertEquals(0f, GpuSceneMesh.leanX(shading))
        assertEquals(0f, GpuSceneMesh.leanZ(shading))
        assertEquals(1f, GpuSceneMesh.occlusion(shading), 0.01f)
    }

    @Test
    fun aSlopeLeansDownhill() {
        // Rising to the east and to the south: the ground faces west and north.
        val shading = middle(field(16, 16) { column, row -> 64 + column + row / 2 }, 16)
        assertTrue(GpuSceneMesh.leanX(shading) < 0f)
        assertTrue(GpuSceneMesh.leanZ(shading) < 0f)
        assertTrue(GpuSceneMesh.leanX(shading) < GpuSceneMesh.leanZ(shading), "steeper along x")
    }

    @Test
    fun aHollowSeesLessSkyThanARidge() {
        val bowl = middle(field(16, 16) { c, r -> 64 + maxOf(abs(c - 8), abs(r - 8)) }, 16)
        val dome = middle(field(16, 16) { c, r -> 80 - maxOf(abs(c - 8), abs(r - 8)) }, 16)
        assertTrue(GpuSceneMesh.occlusion(bowl) < 0.9f)
        assertTrue(GpuSceneMesh.occlusion(dome) > 0.95f)
    }

    @Test
    fun aForestIsNotHillsAndHollows() {
        // Trees two blocks wide and six tall on every third pair of columns of flat ground.
        val forest = field(32, 32) { c, r -> if (c % 4 < 2 && r % 4 < 2) 70 else 64 }
        for (corner in listOf(15, 16, 17)) {
            val shading = ColumnRelief(forest, 0, 0, 32, 32).at(corner, corner)
            assertEquals(0f, GpuSceneMesh.leanX(shading))
            assertEquals(0f, GpuSceneMesh.leanZ(shading))
            assertEquals(1f, GpuSceneMesh.occlusion(shading), 0.01f)
        }
    }

    private fun abs(value: Int) = kotlin.math.abs(value)
}
