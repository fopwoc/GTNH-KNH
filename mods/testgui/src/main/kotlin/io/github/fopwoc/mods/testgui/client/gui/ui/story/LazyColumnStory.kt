package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.LazyColumn
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.foundation.items
import io.github.fopwoc.mods.framework.ui.compose.foundation.rememberLazyListState
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun LazyColumnStory() {
  val fixed = rememberLazyListState()
  val measured = rememberLazyListState()
  Examples {
    Example("10 000 rows, fixed itemHeight — only the visible window is composed") {
      LazyColumn(
          modifier = Modifier.fillMaxWidth().height(70.uu),
          state = fixed,
          itemHeight = 10.uu,
      ) {
        items(10_000) { index -> Text("Row $index") }
      }
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      ) {
        Button(text = "Top", modifier = Modifier.weight(1f)) { fixed.scrollToItem(0) }
        Button(text = "5000", modifier = Modifier.weight(1f)) { fixed.scrollToItem(5000) }
        Button(text = "End", modifier = Modifier.weight(1f)) { fixed.scrollToItem(9_999) }
      }
    }
    Example("Measured heights (itemHeight = null), wrapped rows of varying length") {
      LazyColumn(modifier = Modifier.fillMaxWidth().height(80.uu), state = measured) {
        items(List(300) { index -> "Row $index " + "lorem ".repeat(index % 7) }) { text ->
          Text(text, modifier = Modifier.fillMaxWidth(), style = TextStyle(wrap = true))
        }
      }
      Button(text = "Scroll to 150") { measured.scrollToItem(150) }
    }
  }
}
