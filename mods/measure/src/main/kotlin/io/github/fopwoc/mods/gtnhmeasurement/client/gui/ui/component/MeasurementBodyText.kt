package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementPalette
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.measurementBodyTextStyle

@Composable
fun MeasurementBodyText(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
    color: Color = MeasurementPalette.Foreground,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
) {
  Text(
      text = text,
      modifier = modifier,
      style =
          measurementBodyTextStyle(
              wrap = wrap,
              color = color,
              alignment = alignment,
          ),
  )
}
