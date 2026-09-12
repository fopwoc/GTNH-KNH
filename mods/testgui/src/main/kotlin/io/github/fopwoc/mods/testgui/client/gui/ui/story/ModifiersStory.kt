package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ModifiersStory() {
  var clicks by remember { mutableIntStateOf(0) }
  Examples {
    Example("clickable + hoverBackground on a plain Box (clicks: $clicks)") {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .height(18.uu)
                  .background(Color(0x60303743))
                  .border(Color(0xFF5A5E68))
                  .hoverBackground(Color(0x90405060))
                  .clickable { clicks++ },
          contentAlignment = Alignment.Center,
      ) {
        Text("click me")
      }
    }
    Example("clickable(enabled = false)") {
      Box(
          modifier =
              Modifier.fillMaxWidth().height(14.uu).background(Color(0x40303743)).clickable(
                  enabled = false
              ) {
                clicks++
              }
      )
    }
    Example("background / border / padding order: padding is inside the border") {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
      ) {
        Box(
            modifier =
                Modifier.background(Color(0xA04F81BD)).border(Color(0xFFFFFFFF)).padding(6.uu)
        ) {
          Text("padded")
        }
        Box(modifier = Modifier.border(Color(0xFFFFD54A)).size(30.uu, 14.uu))
      }
    }
    Example("tooltip on any node") {
      Box(
          modifier =
              Modifier.size(40.uu, 12.uu)
                  .background(Color(0xA09BBB59))
                  .tooltip("Boxes can have tooltips too")
      )
    }
  }
}
