package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Checkbox
import io.github.fopwoc.mods.framework.ui.compose.component.native.TextField
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** Hundreds of interactive nodes in one composition; hit testing and hover must stay crisp. */
@Composable
fun DenseWidgetsStory() {
  var checks by remember { mutableStateOf(setOf<Int>()) }
  val fields = remember { List(12) { TextFieldState("field $it") } }
  Examples {
    Example("240 buttons") {
      Column {
        repeat(20) { row ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(1.uu),
          ) {
            repeat(12) { col ->
              Button(text = "${row * 12 + col}", modifier = Modifier.weight(1f)) {}
            }
          }
        }
      }
    }
    Example("96 checkboxes, ${checks.size} checked") {
      Column {
        repeat(24) { row ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = HorizontalArrangement.spacedBy(2.uu),
          ) {
            repeat(4) { col ->
              val id = row * 4 + col
              Checkbox(label = "$id", checked = id in checks, modifier = Modifier.weight(1f)) {
                checks = if (it) checks + id else checks - id
              }
            }
          }
        }
      }
    }
    Example("12 text fields — Tab-less focus by click, one focused at a time") {
      Column {
        fields.forEach { TextField(state = it, modifier = Modifier.fillMaxWidth()) }
      }
      Text("focused: " + (fields.indexOfFirst { it.focused }.takeIf { it >= 0 } ?: "none"))
    }
  }
}
