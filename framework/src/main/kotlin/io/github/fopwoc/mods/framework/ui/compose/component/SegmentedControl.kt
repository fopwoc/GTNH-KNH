package io.github.fopwoc.mods.framework.ui.compose.component

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.MinecraftColor
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.text.styledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit

object SegmentedControlDefaults {
  val Spacing: UiUnit = UiTokens.SmallGap
  val ButtonWidth: UiUnit = UiTokens.StandardButtonWidth
  val SelectedColor: MinecraftColor = MinecraftColor.Yellow

  /**
   * Selected segments keep their button enabled and stand out by label style instead of greying.
   */
  fun selectedLabel(label: String, color: MinecraftColor = SelectedColor): StyledText = styledText {
    withColor(color) {
      withBold {
        +label
      }
    }
  }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    modifier: Modifier = Modifier,
    spacing: UiUnit = SegmentedControlDefaults.Spacing,
    labelOf: (T) -> String = { it.toString() },
    selectedLabelOf: (String) -> StyledText = SegmentedControlDefaults::selectedLabel,
    onSelected: (T) -> Unit,
) {
  Row(
      modifier = modifier,
      horizontalArrangement = HorizontalArrangement.spacedBy(spacing),
      verticalAlignment = VerticalAlignment.CENTER,
  ) {
    options.forEach { option ->
      val label = labelOf(option)
      Button(
          text = if (option == selected) selectedLabelOf(label) else StyledText.of(label),
          modifier = Modifier.width(SegmentedControlDefaults.ButtonWidth),
          onClick = {
            if (option != selected) {
              onSelected(option)
            }
          },
      )
    }
  }
}
