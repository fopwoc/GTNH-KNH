package io.github.fopwoc.mods.framework.ui.compose.layout.hosted

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Size
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import kotlin.math.max

// Natural sizes of the vanilla-hosted controls (GuiButtonExt, GuiCheckBox, GuiTextField,
// GuiSlider).

internal fun measureButtonNaturalSize(
    modifier: Modifier,
    text: StyledText,
    metrics: TextMetrics,
): Size {
  val padding = modifier.padding
  return Size(
      width = max(98, metrics.textWidth(text.formattedString) + 20 + padding.horizontalValue),
      height = max(20, metrics.lineHeight + 10 + padding.verticalValue),
  )
}

internal fun measureCheckboxNaturalSize(
    modifier: Modifier,
    label: StyledText,
    metrics: TextMetrics,
): Size {
  val padding = modifier.padding
  return Size(
      width = max(11, metrics.textWidth(label.formattedString) + 13 + padding.horizontalValue),
      height = max(11, max(metrics.lineHeight, 11) + padding.verticalValue),
  )
}

internal fun measureTextFieldNaturalSize(modifier: Modifier): Size {
  val padding = modifier.padding
  return Size(
      width = max(98, 120 + padding.horizontalValue),
      height = UiTokens.ControlHeight.resolved + padding.verticalValue,
  )
}

internal fun measureSliderNaturalSize(modifier: Modifier): Size {
  val padding = modifier.padding
  return Size(
      width = max(150, 150 + padding.horizontalValue),
      height = UiTokens.ControlHeight.resolved + padding.verticalValue,
  )
}
