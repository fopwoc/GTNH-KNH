package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme

/** A themed surface; `elevated` lifts it a shade for the primary area of a screen. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  Panel(
      modifier = modifier,
      backgroundColor =
          if (elevated) MinecraftTheme.colors.elevatedBackground
          else MinecraftTheme.colors.surfaceBackground,
      borderColor = MinecraftTheme.colors.surfaceBorder,
      content = content,
  )
}
