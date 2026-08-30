package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.ui.page.status.BackupStatusRoute

@SideOnly(Side.CLIENT)
class BackupStatusScreen : ComposeGuiScreen() {
  private var closeRequested = false
  private var refreshToken by mutableIntStateOf(0)

  override val composeBackgroundStyle: ComposeBackgroundStyle =
      ComposeBackgroundStyle.VanillaDefault

  override fun doesGuiPauseGame(): Boolean = false

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
    BackupStatusRoute(
        screenHeight = height,
        refreshToken = refreshToken,
        onClose = {
          closeRequested = true
        },
    )
  }
}
