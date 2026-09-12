package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme

/** A titled [Card]. Content is laid out in a column; give it `weight(1f)` to fill. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
  Card(modifier = modifier, elevated = elevated) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = VerticalArrangement.spacedBy(ScaffoldDefaults.SectionGap),
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
