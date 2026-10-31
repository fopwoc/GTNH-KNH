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

    @Test
    fun aColumnIsItsTintedBlock() {
        assertEquals(0, shader.column(0, 1))
        assertEquals(0xFF808080.toInt(), shader.column(1, 1))
        assertEquals(TerrainShader.applyTint(0xFF939393.toInt(), 0x80FF80), shader.column(4, 6))
        assertEquals(TerrainShader.applyTint(0xFF939393.toInt(), 0x408040), shader.column(5, 6))
    }

    @Test
    fun waterThickensAndDarkensWithDepthAndIsGreenerInTheShallows() {
        fun alpha(argb: Int) = argb ushr 24
        val edge = shader.water(0.0, 1)
        val shallow = shader.water(1.0, 1)
        val deep = shader.water(30.0, 1)
        assertTrue(alpha(edge) < alpha(shallow) && alpha(shallow) < alpha(deep))
        assertTrue(alpha(edge) < 0x80, "the shore is mostly see-through")
        assertTrue(alpha(deep) > 0xF0, "deep water hides its floor")
        assertTrue((deep and 0xFF) > (deep shr 16 and 0xFF), "deep water is blue")
        assertTrue((deep and 0xFF) < (shallow and 0xFF), "deep water is darker")
        val green = { argb: Int -> (argb shr 8 and 0xFF) / (argb and 0xFF).toDouble() }
        assertTrue(green(shallow) > green(deep), "shallows lean green")
    }
}
