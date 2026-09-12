package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.MultiSelectableList
import io.github.fopwoc.mods.framework.ui.compose.component.native.SelectableList
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun SelectableListStory() {
  val items = remember { List(40) { "Item $it" } }
  var single by remember { mutableIntStateOf(2) }
  var multi by remember { mutableStateOf(setOf(1, 3)) }
  Examples {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
    ) {
      Example("Single, 6 visible rows") {
        SelectableList(items = items, selectedIndex = single, visibleRowCount = 6) { single = it }
      }
      Example("Multi: Ctrl/Cmd toggles, Shift extends") {
        MultiSelectableList(items = items, selectedIndices = multi, visibleRowCount = 6) {
          multi = it
        }
      }
    }
    Example("Fill height (rowHeight 10)") {
      SelectableList(
          items = items,
          selectedIndex = single,
          modifier = Modifier.fillMaxWidth().height(60.uu),
          rowHeight = 10.uu,
      ) {
        single = it
      }
    }
    Text("single=$single multi=${multi.sorted()}")
  }
}
