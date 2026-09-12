package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun TextStory() {
  val long =
      "Wrapped text breaks on the width it is given and grows in height; unwrapped text is one line and gets clipped by its container."
  Examples {
    Example("wrap = true") {
      Text(long, modifier = Modifier.fillMaxWidth(), style = TextStyle(wrap = true))
    }
    Example("wrap = false, width 120") { Text(long, modifier = Modifier.width(120.uu)) }
    Example("alignment START / CENTER / END") {
      Text(
          "start",
          modifier = Modifier.fillMaxWidth(),
          style = TextStyle(alignment = HorizontalAlignment.START),
      )
      Text(
          "center",
          modifier = Modifier.fillMaxWidth(),
          style = TextStyle(alignment = HorizontalAlignment.CENTER),
      )
      Text(
          "end",
          modifier = Modifier.fillMaxWidth(),
          style = TextStyle(alignment = HorizontalAlignment.END),
      )
    }
    Example("shadow = false, custom Color") {
      Text("no shadow", style = TextStyle(shadow = false))
      Text("Color(0xFF8FD0FF)", style = TextStyle(color = Color(0xFF8FD0FF)))
    }
    Example("MinecraftColor") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
      ) {
        MinecraftColor.entries.forEach { Text("■", style = TextStyle(color = it.color)) }
      }
    }
    Example("styledText spans") {
      Text(
          styledText {
            +"plain "
            withColor(MinecraftColor.Gold) { +"gold " }
            withBold { +"bold " }
            withItalic { +"italic " }
            withUnderline { +"underline " }
            withStrikethrough { +"strike " }
            withObfuscated { +"xxx" }
          }
      )
    }
    Example("Modifier.tooltip — hover") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(8.uu),
      ) {
        Text("one line", modifier = Modifier.tooltip("A single-line tooltip"))
        Text(
            "multi line",
            modifier = Modifier.tooltip(listOf("First line", "Second line", "Third")),
        )
      }
    }
  }
}
