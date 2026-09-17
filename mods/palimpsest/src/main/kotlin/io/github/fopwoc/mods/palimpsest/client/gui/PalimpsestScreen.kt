package io.github.fopwoc.mods.palimpsest.client.gui

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.benchmark.BenchmarkView

@SideOnly(Side.CLIENT)
class PalimpsestScreen : ComposeMenuScreen() {
  @Composable
  override fun Content() {
    BenchmarkView(screenWidth = width, screenHeight = height, onClose = ::requestClose)
  }
}
