package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal class SliderNode(
    override var modifier: Modifier,
    var value: Double,
    var valueRangeStart: Double,
    var valueRangeEnd: Double,
    var label: String,
    var suffix: String,
    var enabled: Boolean,
    var showDecimal: Boolean,
    var onValueChange: (Double) -> Unit
) : ComposeTreeNode(modifier) {
    private val hostKey: Any = Any()

    override fun toLayoutElement(): LayoutElement = LayoutElement.Slider(
        modifier = modifier,
        hostKey = hostKey,
        value = value,
        valueRangeStart = valueRangeStart,
        valueRangeEnd = valueRangeEnd,
        label = label,
        suffix = suffix,
        enabled = enabled,
        showDecimal = showDecimal,
        onValueChange = onValueChange
    )
}

