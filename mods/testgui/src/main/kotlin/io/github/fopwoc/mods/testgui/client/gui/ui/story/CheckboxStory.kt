package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox

@Composable
fun CheckboxStory() {
  var checked by remember { mutableStateOf(true) }
  Examples {
    Example("Interactive") {
      Checkbox(label = "Checked: $checked", checked = checked) { checked = it }
    }
    Example("Disabled checked / unchecked") {
      Checkbox(label = "Disabled on", checked = true, enabled = false) {}
      Checkbox(label = "Disabled off", checked = false, enabled = false) {}
    }
    Example("Long label wraps? No — labels are single line") {
      Checkbox(label = "A rather long label that keeps going on one line", checked = checked) {
        checked = it
      }
    }
  }
}
