package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun MeasurementScaffold(
    screenWidth: Int,
    title: String,
    summary: String,
    modeBadgeText: String,
    onClose: () -> Unit,
    footerText: String,
    content: @Composable () -> Unit,
) {
  val panelWidth = (screenWidth - 48).coerceIn(240, 340).uu

  Box(modifier = Modifier.fillMaxSize()) {
    Panel(
        modifier = Modifier.width(panelWidth).align(Alignment.Center),
        backgroundColor = MeasurementPalette.ShellBackground,
        borderColor = MeasurementPalette.ShellBorder,
    ) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(4.uu),
      ) {
        MeasurementCard(modifier = Modifier.fillMaxWidth()) {
          Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = VerticalArrangement.spacedBy(3.uu),
          ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
              Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = VerticalArrangement.spacedBy(1.uu),
              ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = measurementTitleTextStyle(),
                )
                MeasurementBodyText(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(),
                    wrap = true,
                    color = MeasurementPalette.Muted,
                )
              }
              Button(
                  text = "Close",
                  onClick = onClose,
              )
            }
            MeasurementBodyText(
                text = modeBadgeText,
                modifier = Modifier.fillMaxWidth(),
                color = MeasurementPalette.Accent,
            )
          }
        }

        MeasurementCard(
            modifier = Modifier.fillMaxWidth(),
            elevated = true,
        ) {
          Box(modifier = Modifier.fillMaxWidth()) {
            content()
          }
        }

        if (footerText.isNotBlank()) {
          MeasurementBodyText(
              text = footerText,
              modifier = Modifier.fillMaxWidth(),
              wrap = true,
              color = MeasurementPalette.Muted,
              alignment = HorizontalAlignment.CENTER,
          )
        }
      }
    }
  }
}
