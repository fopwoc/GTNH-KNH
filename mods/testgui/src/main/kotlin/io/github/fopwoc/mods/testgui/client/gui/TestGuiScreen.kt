package io.github.fopwoc.mods.testgui.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.testgui.TestGuiMod
import io.github.fopwoc.mods.testgui.client.gui.ui.Entrypoint

@SideOnly(Side.CLIENT)
class TestGuiScreen : ComposeGuiScreen() {
  private var closeRequested: Boolean = false
  private var rawMouseClickCount by mutableIntStateOf(0)
  private var callbackEventCount by mutableIntStateOf(0)
  private var lastCallbackEvent by mutableStateOf("none")

  override val composeBackgroundStyle: ComposeBackgroundStyle =
      ComposeBackgroundStyle.VanillaDefault

  override fun doesGuiPauseGame(): Boolean = false

  override fun updateScreen() {
    super.updateScreen()
    if (closeRequested) {
      closeRequested = false
      mc.displayGuiScreen(null)
    }
  }

  override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
    rawMouseClickCount += 1
    TestGuiMod.logger.info(
        "TestGui raw mouseClicked #{} at ({}, {}) button={}",
        rawMouseClickCount,
        mouseX,
        mouseY,
        mouseButton,
    )
    super.mouseClicked(mouseX, mouseY, mouseButton)
  }

  @Composable
  override fun Content() {
    Entrypoint(
        screenWidth = width,
        screenHeight = height,
        debugStatus =
            "debug rawClicks=$rawMouseClickCount callbackEvents=$callbackEventCount last=$lastCallbackEvent",
        onDebugEvent = { event ->
          callbackEventCount += 1
          lastCallbackEvent = event
          TestGuiMod.logger.info(
              "TestGui callback event #{}: {}",
              callbackEventCount,
              event,
          )
        },
        onClose = {
          closeRequested = true
        },
    )
  }
}
