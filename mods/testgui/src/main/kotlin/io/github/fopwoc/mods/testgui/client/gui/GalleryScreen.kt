package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.testgui.client.gui.ui.page.gallery.GalleryView

@SideOnly(Side.CLIENT)
class GalleryScreen : ComposeMenuScreen() {
  @Composable
  override fun Content() {
    GalleryView(screenWidth = width, screenHeight = height, onClose = ::requestClose)
  }
}
