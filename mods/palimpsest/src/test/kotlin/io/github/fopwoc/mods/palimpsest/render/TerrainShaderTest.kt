package io.github.fopwoc.mods.palimpsest.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainShaderTest {
    private val colors = mapOf(1 to 0x808080, 2 to 0x5FA83A, 3 to 0x3F5FDF, 4 to 0x939393, 5 to 0x939393)
    private val shader =
        TerrainShader(
            { colors.getValue(it) },
            { if (it == 4) 1 else if (it == 5) 2 else 0 },
            { biome -> if (biome == 6) 0x80FF80 else 0xFFFFFF },
            { biome -> if (biome == 6) 0x408040 else 0xFFFFFF },
        )

    private fun shaded(color: Int, factor: Int) = TerrainShader.shade(color or (0xFF shl 24), factor)

    private fun render(grid: SampleGrid): IntArray {
        val rgba = ByteArray(grid.side * grid.side * 4)
        shader.shade(grid, rgba)
        return IntArray(grid.side * grid.side) { pixel ->
            val at = pixel * 4
            ((rgba[at + 3].toInt() and 255) shl 24) or
                ((rgba[at].toInt() and 255) shl 16) or
                ((rgba[at + 1].toInt() and 255) shl 8) or
                (rgba[at + 2].toInt() and 255)
        }
    }

    @Test
    fun flatGroundIsTheMiddleShadeAndAbsentCellsAreTransparent() {
        val grid = SampleGrid(4)
        for (z in -1 until 4) for (x in -1 until 4) if (x < 2) grid.set(x, z, 1, 64, 0, 1)
        val pixels = render(grid)
        assertEquals(0xFF808080.toInt(), pixels[0])
        assertEquals(shaded(0x808080, TerrainShader.hillshade(0, 0, 0)), pixels[3 * 4 + 1])
        assertEquals(0, pixels[2])
    }

    @Test
    fun risesTowardsTheNorthWestLightBrightenAndDropsDarken() {
        val grid = SampleGrid(4)
        for (z in -1 until 4) for (x in -1 until 4) grid.set(x, z, 1, 60 + (if (z < 2) z + 1 else 4 - z), 0, 1)
        val pixels = render(grid)
        // Row 0 is one higher than the row north of it: lit.
        assertEquals(shaded(0x808080, TerrainShader.hillshade(0, 1, 0)), pixels[0])
        assertTrue((pixels[0] and 0xFF) > 0x80)
        // Row 3 is one lower than row 2: shaded (cell (1, 3) is an even checker cell).
        assertEquals(shaded(0x808080, TerrainShader.hillshade(0, -1, 0)), pixels[3 * 4 + 1])
        assertTrue((pixels[3 * 4 + 1] and 0xFF) < 0x80)
        // A canopy edge: six blocks above the cell to the west, clamped to the brightest step.
        assertEquals(TerrainShader.SHADES[2], TerrainShader.hillshade(6, 0, 1))
        assertEquals(TerrainShader.SHADES[0], TerrainShader.hillshade(-6, -6, 0))
    }

    @Test
    fun waterFadesWithDepthAndTintableBlocksTakeTheBiomeColor() {
        val grid = SampleGrid(4)
        for (z in 0 until 4) for (x in 0 until 4) grid.set(x, z, 3, 62, 1 + x * 8, 1)
        val pixels = render(grid)
        val shallow = pixels[0] and 0xFF
        val deep = pixels[3] and 0xFF
        assertTrue(deep < shallow, "deep $deep shallow $shallow")
        // Depth 25 caps at the deep-water factor; the odd checker cell dithers six below it.
        assertEquals(TerrainShader.shade(0xFF3F5FDF.toInt(), 160 - 6), pixels[3])
        val tinted = SampleGrid(2)
        for (z in -1 until 2) for (x in -1 until 2) tinted.set(x, z, 4, 64, 0, if (x == 0) 6 else 1)
        val tintedPixels = render(tinted)
        assertEquals(
            shaded(TerrainShader.applyTint(0xFF939393.toInt(), 0x80FF80), TerrainShader.hillshade(0, 0, 0)),
            tintedPixels[0],
        )
        assertEquals(shaded(0x939393, TerrainShader.hillshade(0, 0, 1)), tintedPixels[1])
        val leaves = SampleGrid(1)
        leaves.set(0, 0, 5, 70, 0, 6)
        leaves.set(0, -1, 5, 70, 0, 6)
        assertEquals(shaded(TerrainShader.applyTint(0xFF939393.toInt(), 0x408040), TerrainShader.hillshade(0, 0, 0)), render(leaves)[0])
    }
}
