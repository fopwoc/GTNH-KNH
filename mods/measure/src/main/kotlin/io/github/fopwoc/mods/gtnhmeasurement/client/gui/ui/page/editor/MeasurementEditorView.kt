package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome.MeasurementBodyText
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome.MeasurementPalette
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome.MeasurementScaffold
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome.MeasurementSection
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

@Composable
fun MeasurementEditorView(
    state: MeasurementEditorModel,
    screenWidth: Int = 0,
    onSelectMode: (MeasurementMode) -> Unit = {},
    onDisableMode: () -> Unit = {},
    onClose: () -> Unit = {},
) {
  MeasurementScaffold(
      screenWidth = screenWidth,
      title = "Measure",
      summary = state.summary,
      modeBadgeText = state.modeBadgeText,
      onClose = onClose,
      footerText = state.footerText,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      MeasurementSection(title = "Mode") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = VerticalArrangement.spacedBy(4.uu),
        ) {
          state.availableModes.forEach { mode ->
            Button(
                text = buttonLabel(selectedMode = state.selectedMode, mode = mode),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                  if (mode.isEnabled) onSelectMode(mode) else onDisableMode()
                },
            )
          }
        }
        MeasurementBodyText(
            text = state.contextLabel,
            modifier = Modifier.fillMaxWidth(),
            color = MeasurementPalette.Muted,
        )
      }
    }
  }
}

private fun buttonLabel(selectedMode: MeasurementMode, mode: MeasurementMode): String {
  val prefix = if (selectedMode == mode) "[x]" else "[ ]"
  return "$prefix ${mode.displayName}"
}
