package io.github.fopwoc.mods.framework.ui.compose.component.menu

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme

/** A titled [MenuCard]. Content is laid out in a column; give it `weight(1f)` to fill. */
@Composable
fun MenuSection(
    title: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  MenuCard(modifier = modifier, elevated = elevated) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(MenuDefaults.SectionGap),
    ) {
      Text(
          text = title,
          modifier = Modifier.fillMaxWidth(),
          style = MinecraftTheme.typography.sectionTitle,
      )
      content()
    }
  }
}
