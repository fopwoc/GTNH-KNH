package io.github.fopwoc.mods.framework.ui.compose.layout.list

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Size
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedWidth
import io.github.fopwoc.mods.framework.ui.compose.unit.UiUnit
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
import kotlin.math.max

internal fun measureSelectableListNaturalSize(
    modifier: Modifier,
    items: List<String>,
    rowHeight: UiUnit,
    visibleRowCount: Int,
    metrics: TextMetrics,
): Size {
  val padding = modifier.padding
  val widestItemWidth = items.maxOfOrNull(metrics::textWidth) ?: 0
  val resolvedRowHeight = rowHeight.resolved
  val visibleRows = visibleRowCount.coerceAtLeast(1)
  return Size(
      width = max(120, widestItemWidth + 20 + padding.horizontalValue),
      height =
          max(
              resolvedRowHeight + padding.verticalValue,
              visibleRows * resolvedRowHeight + 8 + padding.verticalValue,
          ),
  )
}

internal fun measureSpacerNaturalSize(modifier: Modifier): Size {
  return Size(
      width = modifier.resolvedFixedWidth ?: 0,
      height = modifier.resolvedFixedHeight ?: 0,
  )
}
