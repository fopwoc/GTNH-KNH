package io.github.fopwoc.mods.palimpsest.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainShaderTest {
    private val colors =
        mapOf(1 to 0x808080, 2 to 0x5FA83A, 3 to 0x3F5FDF, 4 to 0x939393, 5 to 0x939393)
    private val shader =
        TerrainShader(
            { colors.getValue(it) },
            { if (it == 4) 1 else if (it == 5) 2 else 0 },
            { biome -> if (biome == 6) 0x80FF80 else 0xFFFFFF },
            { biome -> if (biome == 6) 0x408040 else 0xFFFFFF },
        )

    private fun shaded(color: Int, factor: Int) =
        TerrainShader.shade(color or (0xFF shl 24), factor)

    @Test
    fun risesTowardsTheNorthWestLightBrightenAndDropsDarken() {
        assertTrue((shaded(0x808080, TerrainShader.hillshade(0, 1, 0)) and 0xFF) > 0x80)
        assertTrue((shaded(0x808080, TerrainShader.hillshade(0, -1, 0)) and 0xFF) < 0x80)
        // A canopy edge: six blocks above the cell to the west, clamped to the brightest step.
        assertEquals(TerrainShader.SHADES[2], TerrainShader.hillshade(6, 6, 1))
        assertEquals(TerrainShader.SHADES[0], TerrainShader.hillshade(-6, -6, 0))
    }

    @Test
    fun aColumnIsItsTintedBlockOrTheWaterOverIt() {
        assertEquals(0, shader.column(0, 0, 1))
        assertEquals(0xFF808080.toInt(), shader.column(1, 0, 1))
        assertEquals(TerrainShader.applyTint(0xFF939393.toInt(), 0x80FF80), shader.column(4, 0, 6))
        assertEquals(TerrainShader.applyTint(0xFF939393.toInt(), 0x408040), shader.column(5, 0, 6))
        val shallow = shader.column(1, 1, 1)
        val deep = shader.column(1, 30, 1)
        assertTrue((deep and 0xFF) > (deep shr 16 and 0xFF), "deep water is blue")
        assertTrue(
            (shallow shr 16 and 0xFF) > (deep shr 16 and 0xFF),
            "the floor shows in shallows",
        )
    }
}
