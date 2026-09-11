package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementChromeDefaults
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.MeasurementPalette
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.theme.measurementTitleTextStyle

@Composable
fun MeasurementScaffold(
    screenWidth: Int,
    screenHeight: Int,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
  val panelWidth = (screenWidth - 40).coerceIn(300, 460).uu
  val panelHeight = (screenHeight - 30).coerceIn(200, 320).uu

  Box(modifier = Modifier.fillMaxSize()) {
    Panel(
        modifier = Modifier.width(panelWidth).height(panelHeight).align(Alignment.Center),
        backgroundColor = MeasurementPalette.ShellBackground,
        borderColor = MeasurementPalette.ShellBorder,
    ) {
      Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = VerticalArrangement.spacedBy(MeasurementChromeDefaults.Gap),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = measurementTitleTextStyle())
            Text(text = subtitle, style = TextStyle(color = MeasurementPalette.Muted))
          }
          Button(text = "Close", modifier = Modifier.width(60.uu), onClick = onClose)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          content()
        }
      }
    }
  }
}
