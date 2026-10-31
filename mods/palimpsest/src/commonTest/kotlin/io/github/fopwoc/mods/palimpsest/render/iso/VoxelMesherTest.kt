package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoxelMesherTest {
    private val colors = mapOf(1 to 0x808080, 2 to 0x3F5FDF, 3 to 0xE0E040, 4 to 0x705030)
    private val palette =
        VoxelPalette(
            byteArrayOf(
                VoxelKind.EMPTY.ordinal.toByte(),
                VoxelKind.SOLID.ordinal.toByte(),
                VoxelKind.WATER.ordinal.toByte(),
                VoxelKind.DECAL.ordinal.toByte(),
                VoxelKind.SOLID.ordinal.toByte(),
            ),
            intArrayOf(0, 1, 2, 3, 4),
            TerrainShader({ colors.getValue(it) }, { 0 }, { 0xFFFFFF }),
        )

    /** A chunk 8 blocks high from Y 60 with [blocks] set at (x, y above 60, z). */
    private fun chunk(vararg blocks: Pair<Triple<Int, Int, Int>, Int>): VoxelChunk {
        val ids = IntArray(16 * 16 * 8)
        for ((at, id) in blocks) ids[(at.second * 16 + at.third) * 16 + at.first] = id
        return VoxelChunk(2, -1, 60, 8, ids, IntArray(256) { 1 })
    }

    private fun GpuSceneMesh.heights(quad: Int) = (0 until 4).map { y(quad * 4 + it) }

    /** Quads lying flat, by the height they lie at. */
    private fun GpuSceneMesh.tops(): List<Float> =
        (0 until quadCount).map { heights(it) }.filter { it.distinct().size == 1 }.map { it[0] }

    @Test
    fun aLoneBlockShowsItsTopAndFourWallsButNothingUnderneath() {
        val mesh = VoxelMesher.mesh(chunk(Triple(3, 2, 4) to 1), palette)
        assertEquals(5, mesh.quadCount)
        assertEquals(listOf(63f), mesh.tops())
        assertEquals(32.0, mesh.originX)
        assertEquals(-16.0, mesh.originZ)
    }

    @Test
    fun likeFacesMergeIntoOneRectangle() {
        val row = (0 until 16).map { Triple(it, 0, 0) to 1 }.toTypedArray()
        // One top, two long walls, two ends.
        assertEquals(5, VoxelMesher.mesh(chunk(*row), palette).quadCount)
    }

    @Test
    fun anOverhangKeepsTheGapUnderIt() {
        val mesh = VoxelMesher.mesh(chunk(Triple(5, 0, 5) to 1, Triple(5, 4, 5) to 4), palette)
        assertEquals(listOf(61f, 65f), mesh.tops().sorted())
        assertEquals(10, mesh.quadCount)
    }

    @Test
    fun aCeilingCutsAwayWhatIsAboveIt() {
        val mesh =
            VoxelMesher.mesh(
                chunk(Triple(5, 0, 5) to 1, Triple(5, 4, 5) to 4),
                palette,
                ceiling = 62,
            )
        assertEquals(listOf(61f), mesh.tops())
    }

    @Test
    fun waterShowsOnlyItsSurfaceColouredOverItsFloor() {
        val lake = arrayOf(Triple(0, 0, 0) to 1, Triple(0, 1, 0) to 2, Triple(0, 2, 0) to 2)
        val mesh = VoxelMesher.mesh(chunk(*lake), palette)
        val surface = (0 until mesh.quadCount).first { mesh.heights(it).all { y -> y == 63f } }
        assertEquals(
            TerrainShader.shade(palette.water(1, 2, 2, 1), TerrainShader.SHADES[1]),
            mesh.colors[surface * 4],
        )
        // The floor under the water has no top of its own.
        assertTrue(61f !in mesh.tops())
    }

    @Test
    fun aDecorationLiesAsAThinDecal() {
        val mesh = VoxelMesher.mesh(chunk(Triple(0, 0, 0) to 1, Triple(0, 1, 0) to 3), palette)
        assertEquals(listOf(61f, 61f + 1f / 16), mesh.tops().sorted())
    }
}
