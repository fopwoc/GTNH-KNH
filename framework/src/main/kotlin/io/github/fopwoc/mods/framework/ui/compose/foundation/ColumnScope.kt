package io.github.fopwoc.mods.framework.ui.compose.foundation

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnParentData

@LayoutScopeMarker
interface ColumnScope {
  fun Modifier.align(alignment: HorizontalAlignment): Modifier

  fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

internal object ColumnScopeInstance : ColumnScope {
  override fun Modifier.align(alignment: HorizontalAlignment): Modifier =
      columnParentData(alignment = alignment)

  override fun Modifier.weight(weight: Float, fill: Boolean): Modifier =
      columnParentData(weight = weight, fill = fill)
}
