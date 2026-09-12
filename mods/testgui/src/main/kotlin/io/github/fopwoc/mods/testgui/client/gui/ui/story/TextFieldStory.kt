package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun TextFieldStory() {
  val empty = remember { TextFieldState() }
  val filled = remember {
    TextFieldState(
        "The quick brown fox jumps over the lazy dog, twice, to test horizontal scrolling"
    )
  }
  Examples {
    Example("Placeholder") {
      TextField(state = empty, modifier = Modifier.fillMaxWidth(), placeholder = "Type here")
    }
    Example("Prefilled, longer than the field") {
      TextField(state = filled, modifier = Modifier.fillMaxWidth())
    }
    Example("Disabled") {
      TextField(state = remember { TextFieldState("read only") }, enabled = false)
    }
    Example("Programmatic focus and selection") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Button(text = "Focus", modifier = Modifier.weight(1f)) { filled.requestFocus() }
        Button(text = "Blur", modifier = Modifier.weight(1f)) { filled.clearFocus() }
        Button(text = "Select all", modifier = Modifier.weight(1f)) { filled.selectAll() }
        Button(text = "End", modifier = Modifier.weight(1f)) { filled.placeCursorAtEnd() }
      }
    }
    Text(
        "focused=${filled.focused} selection=${filled.selection.min}..${filled.selection.max} length=${filled.text.length}"
    )
    Text("Keys: click places the cursor, drag or Shift+arrows select, Ctrl/Cmd+A/C/X/V.")
  }
}
