package io.github.fopwoc.mods.framework.ui.compose.component.menu

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme

@Composable
fun MenuBodyText(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = true,
    color: Color? = null,
    alignment: HorizontalAlignment = HorizontalAlignment.START,
) {
  val base = MinecraftTheme.typography.body
  Text(
      text = text,
      modifier = modifier,
      style = base.copy(color = color ?: base.color, wrap = wrap, alignment = alignment),
  )
}
