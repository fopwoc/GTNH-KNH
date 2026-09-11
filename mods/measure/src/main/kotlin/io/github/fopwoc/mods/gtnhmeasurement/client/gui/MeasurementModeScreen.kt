package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeGuiScreen
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
class MeasurementModeScreen : ComposeGuiScreen() {
  private var closeRequested: Boolean = false
  private var refreshToken by mutableIntStateOf(0)

  override val composeBackgroundStyle: ComposeBackgroundStyle = ComposeBackgroundStyle.None

  override fun doesGuiPauseGame(): Boolean = false

  override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
    val toggleKey = MeasurementKeyBindings.openMenu.keyCode
    if (toggleKey != Keyboard.KEY_NONE && keyCode == toggleKey) {
      mc.displayGuiScreen(null)
      return true
    }
    // Cmd/Ctrl+A selects every measurement in the list (a focused text field keeps its own).
    if (keyCode == Keyboard.KEY_A && MeasurementShortcutScheme.editorModifierDown()) {
      mc.theWorld?.provider?.dimensionId?.let { dimensionId ->
        MeasurementSelectionState.replaceSelection(
            MeasurementSelectionState.measurementsForDimension(dimensionId).map { it.id }
        )
      }
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
        onClose = {
          closeRequested = true
        },
    )
  }
}
