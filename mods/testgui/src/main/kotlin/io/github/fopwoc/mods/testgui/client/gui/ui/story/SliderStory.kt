package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun SliderStory() {
  var percent by remember { mutableDoubleStateOf(40.0) }
  var ratio by remember { mutableDoubleStateOf(0.25) }
  var offset by remember { mutableDoubleStateOf(0.0) }
  Examples {
    Example("Integer with suffix") {
      Slider(
          value = percent,
          onValueChange = { percent = it },
          modifier = Modifier.fillMaxWidth(),
          valueRange = 0.0..100.0,
          label = "Power",
          suffix = "%",
          showDecimal = false,
      )
    }
    Example("Decimal 0..1") {
      Slider(
          value = ratio,
          onValueChange = { ratio = it },
          modifier = Modifier.fillMaxWidth(),
          label = "Ratio",
      )
    }
    Example("Negative range, narrow") {
      Slider(
          value = offset,
          onValueChange = { offset = it },
          modifier = Modifier.width(120.uu),
          valueRange = -10.0..10.0,
          label = "Offset",
          showDecimal = false,
      )
    }
    Example("Disabled") {
      Slider(
          value = 0.5,
          onValueChange = {},
          modifier = Modifier.fillMaxWidth(),
          label = "Locked",
          enabled = false,
      )
    }
  }
}
