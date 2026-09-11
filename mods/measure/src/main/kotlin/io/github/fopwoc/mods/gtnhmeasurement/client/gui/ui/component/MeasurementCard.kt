package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementPalette

@Composable
fun MeasurementCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  Panel(
      modifier = modifier,
      backgroundColor =
          if (elevated) MeasurementPalette.ElevatedBackground
          else MeasurementPalette.SurfaceBackground,
      borderColor = MeasurementPalette.SurfaceBorder,
      content = content,
  )
}
