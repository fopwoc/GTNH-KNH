package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementPalette

@Composable
fun KeyChip(keys: String) {
  Box(
      modifier =
          Modifier.background(MeasurementPalette.ChipBackground)
              .border(MeasurementPalette.ChipBorder)
              .padding(horizontal = 4.uu, vertical = 2.uu)
  ) {
    Text(text = keys, style = TextStyle(color = MeasurementPalette.Gold))
  }
}
