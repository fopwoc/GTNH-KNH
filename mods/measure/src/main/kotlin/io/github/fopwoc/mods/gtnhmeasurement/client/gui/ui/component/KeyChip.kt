package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun KeyChip(keys: String) {
  Box(
      modifier =
          Modifier.background(MinecraftTheme.colors.chipBackground)
              .border(MinecraftTheme.colors.chipBorder)
              .padding(horizontal = 4.uu, vertical = 2.uu)
  ) {
    Text(text = keys, style = MinecraftTheme.typography.sectionTitle)
  }
}
