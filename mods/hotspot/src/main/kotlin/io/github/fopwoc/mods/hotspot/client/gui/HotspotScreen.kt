package io.github.fopwoc.mods.hotspot.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.hotspot.client.HotspotKeyBindings
import io.github.fopwoc.mods.hotspot.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
class HotspotScreen : ComposeGuiScreen() {
  private var closeRequested = false
  private var refreshToken by mutableIntStateOf(0)

  override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.None

  override fun doesGuiPauseGame(): Boolean = false

  override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
    val toggleKey = HotspotKeyBindings.openMenu.keyCode
    if (toggleKey != Keyboard.KEY_NONE && keyCode == toggleKey) {
      mc.displayGuiScreen(null)
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
      refreshToken += 1
      return true
    }
    return false
  }

  override fun updateScreen() {
    super.updateScreen()
    refreshToken += 1
    if (closeRequested) {
      closeRequested = false
      mc.displayGuiScreen(null)
    }
  }

  @Composable
  override fun Content() {
    Entrypoint(
        screenWidth = width,
        screenHeight = height,
        refreshToken = refreshToken,
        onClose = { closeRequested = true },
    )
  }
}
