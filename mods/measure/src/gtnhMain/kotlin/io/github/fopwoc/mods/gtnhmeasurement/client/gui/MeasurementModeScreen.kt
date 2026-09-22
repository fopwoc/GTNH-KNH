package io.github.fopwoc.mods.gtnhmeasurement.client.gui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.screen.ComposeMenuScreen
import net.minecraft.client.Minecraft
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.Entrypoint
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme

class MeasurementModeScreen : ComposeMenuScreen(toggleKey = MeasurementKeyBindings.openMenu) {
    override fun onUnhandledKey(press: KeyPress): Boolean {
        if (super.onUnhandledKey(press)) {
            return true
        }
        // Cmd/Ctrl+A selects every measurement in the list (a focused text field keeps its own).
        if (press.key == Key.A && MeasurementShortcutScheme.editorModifierDown()) {
            Minecraft.getMinecraft().theWorld?.provider?.dimensionId?.let { dimensionId ->
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
            onClose = ::close,
        )
    }
}
