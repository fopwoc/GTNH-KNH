package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** Key chip followed by its description; shared by the menu and the in-world hint box. */
@Composable
fun ShortcutRow(
    keys: String,
    action: String,
    modifier: Modifier = Modifier,
    actionColor: Color? = null,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
      verticalAlignment = VerticalAlignment.CENTER,
  ) {
    if (keys.isNotEmpty()) {
      KeyChip(keys)
    }
    Text(
        text = action,
        style =
            MinecraftTheme.typography.body.let {
              if (actionColor == null) it else it.copy(color = actionColor)
            },
    )
  }
}
