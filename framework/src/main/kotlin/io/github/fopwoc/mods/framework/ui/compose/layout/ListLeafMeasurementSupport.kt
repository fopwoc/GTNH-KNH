package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedWidth
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import kotlin.math.max

internal fun measureSelectableListNaturalSize(
    element: LayoutElement.SelectableList,
    metrics: TextMetrics
): Size {
    val padding = element.modifier.padding
    val widestItemWidth = element.items.maxOfOrNull(metrics::textWidth) ?: 0
    val rowHeight = element.rowHeight.resolved
    val visibleRows = element.visibleRowCount.coerceAtLeast(1)
    return Size(
        width = max(120, widestItemWidth + 20 + padding.horizontalValue),
        height = max(
            rowHeight + padding.verticalValue,
            visibleRows * rowHeight + 8 + padding.verticalValue
        )
    )
}

internal fun measureSpacerNaturalSize(element: LayoutElement.Spacer): Size {
    return Size(
        width = element.modifier.resolvedFixedWidth ?: 0,
        height = element.modifier.resolvedFixedHeight ?: 0
    )
}

