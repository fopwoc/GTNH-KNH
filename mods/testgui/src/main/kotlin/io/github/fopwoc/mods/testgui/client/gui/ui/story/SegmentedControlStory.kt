package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.SegmentedControl
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun SegmentedControlStory() {
  var seconds by remember { mutableIntStateOf(5) }
  Examples {
    Example("Fill width") {
      SegmentedControl(
          options = listOf(3, 5, 10, 15),
          selected = seconds,
          modifier = Modifier.fillMaxWidth(),
          labelOf = { "$it s" },
      ) {
        seconds = it
      }
    }
    Example("Natural width") {
      SegmentedControl(options = listOf("A", "B"), selected = "A", onSelected = {})
    }
    Example("Many options, fixed width — labels squeeze") {
      SegmentedControl(
          options = (1..8).toList(),
          selected = 4,
          modifier = Modifier.width(160.uu),
          onSelected = {},
      )
    }
    Text("selected=$seconds")
  }
}
