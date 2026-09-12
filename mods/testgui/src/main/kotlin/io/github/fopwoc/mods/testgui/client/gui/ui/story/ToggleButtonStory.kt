package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.ToggleButton
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

@Composable
fun ToggleButtonStory() {
  var on by remember { mutableStateOf(false) }
  Examples {
    Example("Interactive") {
      ToggleButton(label = "Automation", checked = on, modifier = Modifier.fillMaxWidth()) {
        on = it
      }
    }
    Example("Disabled") { ToggleButton(label = "Locked", checked = true, enabled = false) {} }
  }
}
