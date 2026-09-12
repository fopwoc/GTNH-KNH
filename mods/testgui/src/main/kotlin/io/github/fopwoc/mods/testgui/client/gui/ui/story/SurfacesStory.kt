package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.Card
import io.github.fopwoc.mods.framework.ui.compose.component.Dialog
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun SurfacesStory() {
  var dialog by remember { mutableStateOf(false) }
  Examples {
    Example("Panel — vanilla-ish defaults; Card / Card(elevated) — themed") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Panel(modifier = Modifier.weight(1f)) { Text("Panel") }
        Card(modifier = Modifier.weight(1f)) { Text("Card") }
        Card(modifier = Modifier.weight(1f), elevated = true) { Text("elevated") }
      }
    }
    Example("Panel with custom colours and padding") {
      Panel(
          modifier = Modifier.fillMaxWidth(),
          backgroundColor = Color(0x8020304A),
          borderColor = Color(0xFF6FA8DC),
          contentPadding = 12.uu,
      ) {
        Text("backgroundColor, borderColor, contentPadding")
      }
    }
    Example("Section — titled Card") {
      Section(title = "Section title", modifier = Modifier.fillMaxWidth()) { Text("content") }
    }
    Example("Dialog — centred in whatever box it is given") {
      Button(text = if (dialog) "Hide dialog" else "Show dialog") { dialog = !dialog }
      if (dialog) {
        Box(modifier = Modifier.fillMaxWidth().height(90.uu)) {
          Dialog(title = "Dialog", text = "Something went wrong, in red by default.") {
            dialog = false
          }
        }
      }
    }
  }
}
