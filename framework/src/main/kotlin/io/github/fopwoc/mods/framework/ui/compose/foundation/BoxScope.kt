package io.github.fopwoc.mods.framework.ui.compose.foundation

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxParentData

@LayoutScopeMarker
interface BoxScope {
  fun Modifier.align(alignment: Alignment): Modifier

  fun Modifier.matchParentWidth(): Modifier

  fun Modifier.matchParentHeight(): Modifier

  fun Modifier.matchParentSize(): Modifier
}

internal object BoxScopeInstance : BoxScope {
  override fun Modifier.align(alignment: Alignment): Modifier = boxParentData(alignment = alignment)

  override fun Modifier.matchParentWidth(): Modifier = boxParentData(matchParentWidth = true)

  override fun Modifier.matchParentHeight(): Modifier = boxParentData(matchParentHeight = true)

  override fun Modifier.matchParentSize(): Modifier = boxParentData(matchParentSize = true)
}
