package io.github.fopwoc.mods.testgui.client.gui.ui.chrome

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

object TestGuiPalette {
  val Gold: Color = Color(0xFFFFD54A)
  val Foreground: Color = Color(0xFFE6E6E6)
  val Muted: Color = Color(0xFFBCBCBC)
  val Dim: Color = Color(0xFF9E9E9E)
  val Accent: Color = Color(0xFF8FD0FF)
  val Success: Color = Color(0xFF9AE28D)
  val ShellBackground: Color = Color(0xB0141418)
  val ShellBorder: Color = Color(0xFF4A4A56)
  val SurfaceBackground: Color = Color(0x7A101216)
  val SurfaceBorder: Color = Color(0xFF343844)
  val ElevatedBackground: Color = Color(0x60303743)
}

object TestGuiChromeDefaults {
  val SidebarWidth = 148.uu
  val Gap = 8.uu
  val SectionGap = 6.uu
}

fun titleTextStyle(): TextStyle = TextStyle(color = TestGuiPalette.Foreground)

fun sectionTitleTextStyle(): TextStyle = TextStyle(color = TestGuiPalette.Gold)

fun bodyTextStyle(wrap: Boolean = false): TextStyle =
    TextStyle(
        color = TestGuiPalette.Foreground,
        wrap = wrap,
    )

fun mutedTextStyle(
    wrap: Boolean = false,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
): TextStyle =
    TextStyle(
        color = TestGuiPalette.Muted,
        wrap = wrap,
        alignment = alignment,
    )

fun accentTextStyle(wrap: Boolean = false): TextStyle =
    TextStyle(
        color = TestGuiPalette.Accent,
        wrap = wrap,
    )

@Composable
fun TestGuiCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  Panel(
      modifier = modifier,
      backgroundColor =
          if (elevated) TestGuiPalette.ElevatedBackground else TestGuiPalette.SurfaceBackground,
      borderColor = TestGuiPalette.SurfaceBorder,
      content = content,
  )
}

@Composable
fun SectionBlock(
    title: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  TestGuiCard(
      modifier = modifier,
      elevated = elevated,
  ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = VerticalArrangement.spacedBy(6.uu),
    ) {
      Text(
          text = title,
          modifier = Modifier.fillMaxWidth(),
          style = sectionTitleTextStyle(),
      )
      content()
    }
  }
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
) {
  Text(
      text = text,
      modifier = modifier,
      style = bodyTextStyle(wrap = wrap),
  )
}

@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
) {
  Text(
      text = text,
      modifier = modifier,
      style = mutedTextStyle(wrap = wrap, alignment = alignment),
  )
}

@Composable
fun AccentText(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
) {
  Text(
      text = text,
      modifier = modifier,
      style = accentTextStyle(wrap = wrap),
  )
}
