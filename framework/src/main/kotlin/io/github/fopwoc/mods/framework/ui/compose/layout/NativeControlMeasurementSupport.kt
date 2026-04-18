package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import kotlin.math.max

internal fun measureButtonNaturalSize(
    element: LayoutElement.Button,
    metrics: TextMetrics
): Size {
    val padding = element.modifier.padding
    return Size(
        width = max(98, metrics.textWidth(element.text.formattedString) + 20 + padding.horizontalValue),
        height = max(20, metrics.lineHeight + 10 + padding.verticalValue)
    )
}

internal fun measureCheckboxNaturalSize(
    element: LayoutElement.Checkbox,
    metrics: TextMetrics
): Size {
    val padding = element.modifier.padding
    return Size(
        width = max(11, metrics.textWidth(element.label.formattedString) + 13 + padding.horizontalValue),
        height = max(11, max(metrics.lineHeight, 11) + padding.verticalValue)
    )
}

internal fun measureTextFieldNaturalSize(element: LayoutElement.TextField): Size {
    val padding = element.modifier.padding
    return Size(
        width = max(98, 120 + padding.horizontalValue),
        height = UiTokens.ControlHeight.resolved + padding.verticalValue
    )
}

internal fun measureSliderNaturalSize(element: LayoutElement.Slider): Size {
    val padding = element.modifier.padding
    return Size(
        width = max(150, 150 + padding.horizontalValue),
        height = UiTokens.ControlHeight.resolved + padding.verticalValue
    )
}

