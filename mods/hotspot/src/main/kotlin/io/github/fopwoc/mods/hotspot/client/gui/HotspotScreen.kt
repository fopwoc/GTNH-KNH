package io.github.fopwoc.mods.hotspot.client.gui

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.hotspot.client.HotspotKeyBindings
import io.github.fopwoc.mods.hotspot.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
class HotspotScreen : ComposeMenuScreen(toggleKey = HotspotKeyBindings.openMenu) {
  override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
    if (super.onUnhandledKey(typedChar, keyCode)) {
      return true
    }
    // Cmd/Ctrl+A picks every listed tile entity of the focused chunk.
    if (keyCode == Keyboard.KEY_A && GuiScreen.isCtrlKeyDown()) {
      val chunk = ProfileStore.focusedChunk ?: return true
      val listed = ProfileStore.chunk(chunk)?.tileEntities.orEmpty()
      ProfileStore.setSelectedInChunk(
          chunk,
          listed.map { TileEntityRef(chunk.dimensionId, it.x, it.y, it.z) },
      )
      refreshNow()
      return true
    }
    return false
  }

  @Composable
  override fun Content() {
    Entrypoint(
        screenWidth = width,
        screenHeight = height,
        refreshToken = refreshToken,
        onClose = ::requestClose,
    )
  }
}
