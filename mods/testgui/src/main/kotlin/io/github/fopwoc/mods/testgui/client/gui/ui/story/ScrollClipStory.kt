package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.LazyColumn
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.runtime.rememberScrollState
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/**
 * Clipping and hit testing under nested scrolling; nothing here may draw or click outside its box.
 */
@Composable
fun ScrollClipStory() {
  Examples {
    Example("Scroll inside scroll inside the story pane; offset content must clip at each border") {
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .height(70.uu)
                  .border(Color(0xFFFF8080))
                  .verticalScroll(rememberScrollState())
      ) {
        repeat(6) { outer ->
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(40.uu)
                      .border(Color(0xFF80FF80))
                      .horizontalScroll(rememberScrollState())
          ) {
            repeat(12) { inner ->
              Box(modifier = Modifier.offset(y = (inner % 3 * 6).uu).padding(2.uu)) {
                Button(text = "$outer.$inner") {}
              }
            }
          }
        }
      }
    }
    Example("LazyColumn of rows that each scroll horizontally") {
      LazyColumn(modifier = Modifier.fillMaxWidth().height(60.uu), itemHeight = 22.uu) {
        items(200) { index ->
          Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = HorizontalArrangement.spacedBy(2.uu),
          ) {
            repeat(20) { Button(text = "$index/$it") {} }
          }
        }
      }
    }
    Example("Tooltips near the pane edges must stay on screen") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.SpaceBetween,
      ) {
        Text(
            "left edge",
            modifier =
                Modifier.tooltip(
                    "A tooltip that is quite long so it would overflow to the left if not clamped"
                ),
        )
        Text(
            "right edge",
            modifier =
                Modifier.tooltip(
                    "A tooltip that is quite long so it would overflow to the right if not clamped"
                ),
        )
      }
    }
  }
}
