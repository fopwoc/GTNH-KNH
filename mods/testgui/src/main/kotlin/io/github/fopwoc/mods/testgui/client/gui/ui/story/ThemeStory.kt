package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.Card
import io.github.fopwoc.mods.framework.ui.compose.component.Section
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.theme.MinecraftTheme
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
fun ThemeStory() {
  val colors = MinecraftTheme.colors
  val swatches =
      listOf(
          "foreground" to colors.foreground,
          "muted" to colors.muted,
          "title" to colors.title,
          "accent" to colors.accent,
          "success" to colors.success,
          "warning" to colors.warning,
          "danger" to colors.danger,
          "shellBackground" to colors.shellBackground,
          "shellBorder" to colors.shellBorder,
          "surfaceBackground" to colors.surfaceBackground,
          "surfaceBorder" to colors.surfaceBorder,
          "elevatedBackground" to colors.elevatedBackground,
          "chipBackground" to colors.chipBackground,
          "chipBorder" to colors.chipBorder,
      )
  Examples {
    Example("MinecraftTheme.colors") {
      Column {
        swatches.forEach { (name, color) ->
          Row(
              horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
              verticalAlignment = VerticalAlignment.CENTER,
          ) {
            Box(modifier = Modifier.size(10.uu).background(color).border(Color(0xFF808080)))
            Text(name)
          }
        }
      }
    }
    Example("MinecraftTheme.typography") {
      Text("title", style = MinecraftTheme.typography.title)
      Text("sectionTitle", style = MinecraftTheme.typography.sectionTitle)
      Text("body", style = MinecraftTheme.typography.body)
      Text("muted", style = MinecraftTheme.typography.muted)
    }
    Example("MinecraftTheme(colors = …) restyles a subtree") {
      MinecraftTheme(
          colors = colors.copy(title = Color(0xFFFF8080), surfaceBorder = Color(0xFFFF8080))
      ) {
        Section(title = "Red-themed section", modifier = Modifier.fillMaxWidth()) {
          Card(modifier = Modifier.fillMaxWidth()) { Text("inner card follows too") }
        }
      }
    }
  }
}
