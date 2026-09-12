package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import androidx.compose.runtime.Composable
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeMenuScreen
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme
import org.lwjgl.input.Keyboard

@SideOnly(Side.CLIENT)
class MeasurementModeScreen : ComposeMenuScreen(toggleKey = MeasurementKeyBindings.openMenu) {
  override fun onUnhandledKey(typedChar: Char, keyCode: Int): Boolean {
    if (super.onUnhandledKey(typedChar, keyCode)) {
      return true
    }
    // Cmd/Ctrl+A selects every measurement in the list (a focused text field keeps its own).
    if (keyCode == Keyboard.KEY_A && MeasurementShortcutScheme.editorModifierDown()) {
      mc.theWorld?.provider?.dimensionId?.let { dimensionId ->
        MeasurementSelectionState.replaceSelection(
            MeasurementSelectionState.measurementsForDimension(dimensionId).map { it.id }
        )
      }
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
