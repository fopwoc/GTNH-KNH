package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ButtonStory() {
  var clicks by remember { mutableIntStateOf(0) }
  Examples {
    Example("Natural width, fill width, fixed width") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Button(text = "Natural") { clicks++ }
        Button(text = "Weight 1", modifier = Modifier.weight(1f)) { clicks++ }
        Button(text = "40", modifier = Modifier.width(40.uu)) { clicks++ }
      }
    }
    Example("Disabled") { Button(text = "Disabled", enabled = false) {} }
    Example("Styled label") {
      Button(
          text =
              styledText {
                withColor(MinecraftColor.Gold) { +"Gold" }
                +" and "
                withBold { withColor(MinecraftColor.Aqua) { +"bold aqua" } }
              }
      ) {
        clicks++
      }
    }
    Example("Tooltip") {
      Button(text = "Hover me", modifier = Modifier.tooltip("Modifier.tooltip(text)")) { clicks++ }
    }
    Text("Clicks: $clicks")
  }
}
