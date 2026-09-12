package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

/** A small centred message with one button, e.g. shown instead of a screen that cannot open. */
@Composable
fun Dialog(
    title: String,
    text: String,
    buttonText: String = "Close",
    textColor: Color? = null,
    onButton: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Panel(
        modifier = Modifier.width(260.uu).padding(10.uu),
        backgroundColor = MinecraftTheme.colors.shellBackground,
        borderColor = MinecraftTheme.colors.shellBorder,
    ) {
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = VerticalArrangement.spacedBy(8.uu),
      ) {
        Text(text = title, style = MinecraftTheme.typography.title)
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style =
                MinecraftTheme.typography.body.copy(
                    color = textColor ?: MinecraftTheme.colors.danger,
                    wrap = true,
                ),
        )
        Button(text = buttonText, modifier = Modifier.fillMaxWidth(), onClick = onButton)
      }
    }
  }
}
