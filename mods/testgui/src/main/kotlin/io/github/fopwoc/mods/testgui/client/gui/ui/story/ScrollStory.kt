package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ScrollStory() {
  val vertical = rememberScrollState()
  val horizontal = rememberScrollState()
  Examples {
    Example("Modifier.verticalScroll — wheel over it; offset ${vertical.value}") {
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .height(60.uu)
                  .border(Color(0xFF444444))
                  .verticalScroll(vertical)
      ) {
        repeat(40) { Text("Line $it") }
      }
    }
    Example("Modifier.horizontalScroll — Shift+wheel; offset ${horizontal.value}") {
      Row(
          modifier = Modifier.fillMaxWidth().border(Color(0xFF444444)).horizontalScroll(horizontal),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        repeat(30) { Button(text = "B$it") {} }
      }
    }
    Example("Nested: inner list scrolls first, then the story pane takes over at the ends") {
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .height(50.uu)
                  .border(Color(0xFF444444))
                  .verticalScroll(rememberScrollState())
      ) {
        repeat(20) { Text("Inner $it") }
      }
    }
  }
}
