package io.github.fopwoc.mods.framework.ui.compose.node

import io.github.fopwoc.mods.framework.ui.compose.model.element.HostedWidgetKey
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
    var onValueChange: (Double) -> Unit,
) : ComposeTreeNode(modifier) {
  internal val hostKey = HostedWidgetKey()
}
