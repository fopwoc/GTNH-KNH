package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** One labelled state inside a story. */
@Composable
fun Example(label: String, content: @Composable () -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(3.uu),
  ) {
    Text(text = label, style = MinecraftTheme.typography.muted)
    content()
  }
}

/** Vertical list of [Example]s with story spacing. */
@Composable
fun Examples(content: @Composable () -> Unit) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = VerticalArrangement.spacedBy(8.uu),
  ) {
    content()
  }
}
