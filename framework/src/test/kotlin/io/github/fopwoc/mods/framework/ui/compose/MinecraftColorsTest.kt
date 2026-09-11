package io.github.fopwoc.mods.framework.ui.compose

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftColorsTest {
  @Test
  fun minecraftFormattingPaletteMatchesVanillaRgbValues() {
    assertEquals(Color(0xFF000000), MinecraftColor.Black.color)
    assertEquals(Color(0xFFFFAA00), MinecraftColor.Gold.color)
    assertEquals(Color(0xFF55FF55), MinecraftColor.Green.color)
    assertEquals(Color(0xFFFF5555), MinecraftColor.Red.color)
    assertEquals(Color(0xFFFFFFFF), MinecraftColor.White.color)
  }
}
