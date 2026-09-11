package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.chrome

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

object MeasurementPalette {
  val Gold: Color = Color(0xFFFFD54A)
  val Foreground: Color = Color(0xFFE6E6E6)
  val Muted: Color = Color(0xFFBCBCBC)
  val Accent: Color = Color(0xFF8FD0FF)
  val Success: Color = Color(0xFF9AE28D)
  val Warning: Color = Color(0xFFFFB45A)
  val Danger: Color = Color(0xFFFFAAAA)
  val ShellBackground: Color = Color(0xB0141418)
  val ShellBorder: Color = Color(0xFF4A4A56)
  val SurfaceBackground: Color = Color(0x7A101216)
  val SurfaceBorder: Color = Color(0xFF343844)
  val ElevatedBackground: Color = Color(0x60303743)
  val ChipBackground: Color = Color(0xCC2A2D34)
  val ChipBorder: Color = Color(0xFF5A5E68)
}

object MeasurementChromeDefaults {
  val Gap = 5.uu
  val SectionGap = 4.uu
}

fun measurementTitleTextStyle(): TextStyle = TextStyle(color = MeasurementPalette.Foreground)

fun measurementSectionTitleTextStyle(): TextStyle = TextStyle(color = MeasurementPalette.Gold)

fun measurementBodyTextStyle(
    wrap: Boolean = false,
    color: Color = MeasurementPalette.Foreground,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
): TextStyle =
    TextStyle(
        color = color,
        wrap = wrap,
        alignment = alignment,
    )

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

@Composable
fun MeasurementSection(
    title: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  MeasurementCard(
      modifier = modifier,
      elevated = elevated,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(4.uu),
    ) {
      Text(
          text = title,
          modifier = Modifier.fillMaxWidth(),
          style = measurementSectionTitleTextStyle(),
      )
      content()
    }
  }
}

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
