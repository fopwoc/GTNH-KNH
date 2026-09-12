package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.Tabs
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

@Composable
fun TabsStory() {
  var tab by remember { mutableStateOf("First") }
  Examples {
    Example("Tabs(options, selected, labelOf, onSelected) { tab -> content }") {
      Tabs(
          options = listOf("First", "Second", "Third"),
          selected = tab,
          modifier = Modifier.fillMaxWidth(),
          onSelected = { tab = it },
      ) {
        Text("Content of $it")
      }
    }
  }
}
