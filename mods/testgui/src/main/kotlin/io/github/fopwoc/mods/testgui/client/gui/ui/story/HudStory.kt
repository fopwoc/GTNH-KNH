package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay

@Composable
fun HudStory() {
  var enabled by remember { mutableStateOf(TestGuiHudOverlay.enabled) }
  Examples {
    Example(
        "ComposeHudOverlay: four HudAnchor corners, a frame-clock animation and a tick readout"
    ) {
      Button(text = if (enabled) "Turn HUD off" else "Turn HUD on") {
        enabled = TestGuiHudOverlay.toggle()
      }
      Text("Close this screen to see it. Also /testgui hud.")
    }
  }
}
