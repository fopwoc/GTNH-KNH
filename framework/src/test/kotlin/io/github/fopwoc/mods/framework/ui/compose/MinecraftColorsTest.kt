package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftColorsTest {
    @Test
    fun minecraftFormattingPaletteMatchesVanillaRgbValues() {
        assertEquals(Color.rgb(red = 0x00, green = 0x00, blue = 0x00), MinecraftColor.Black.color)
        assertEquals(Color.rgb(red = 0xFF, green = 0xAA, blue = 0x00), MinecraftColor.Gold.color)
        assertEquals(Color.rgb(red = 0x55, green = 0xFF, blue = 0x55), MinecraftColor.Green.color)
        assertEquals(Color.rgb(red = 0xFF, green = 0x55, blue = 0x55), MinecraftColor.Red.color)
        assertEquals(Color.rgb(red = 0xFF, green = 0xFF, blue = 0xFF), MinecraftColor.White.color)
    }
}

