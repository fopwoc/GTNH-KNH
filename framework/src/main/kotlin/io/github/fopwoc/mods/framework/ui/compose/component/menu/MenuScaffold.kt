package io.github.fopwoc.mods.framework.ui.compose.component.menu

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Panel
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/**
 * The frame of an in-game mod menu: a centred panel sized to the screen, a title/subtitle header
 * with a Close button, and the content below. Pair with `ComposeMenuScreen`.
 */
@Composable
fun MenuScaffold(
    screenWidth: Int,
    screenHeight: Int,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    maxWidth: Int = MenuDefaults.MaxWidth,
    maxHeight: Int = MenuDefaults.MaxHeight,
    content: @Composable () -> Unit,
) {
  val panelWidth = (screenWidth - 40).coerceIn(MenuDefaults.MinWidth, maxWidth).uu
  val panelHeight = (screenHeight - 30).coerceIn(MenuDefaults.MinHeight, maxHeight).uu

  Box(modifier = Modifier.fillMaxSize()) {
    Panel(
        modifier = Modifier.width(panelWidth).height(panelHeight).align(Alignment.Center),
        backgroundColor = MinecraftTheme.colors.shellBackground,
        borderColor = MinecraftTheme.colors.shellBorder,
    ) {
      Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = VerticalArrangement.spacedBy(MenuDefaults.Gap),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = HorizontalArrangement.spacedBy(6.uu),
            verticalAlignment = VerticalAlignment.CENTER,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MinecraftTheme.typography.title)
            Text(text = subtitle, style = MinecraftTheme.typography.muted)
          }
          Button(text = "Close", modifier = Modifier.width(60.uu), onClick = onClose)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
      }
    }
  }
}
