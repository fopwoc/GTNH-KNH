package io.github.fopwoc.mods.framework.ui.compose.layout.stack

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect

internal data class StackMeasurement(
    val children: List<LayoutNode>,
    val contentMainAxisSize: Int,
    val contentCrossAxisSize: Int,
)

internal data class StackMeasureSpec(
    val axis: StackAxis,
    val maxWidth: Int,
    val maxHeight: Int,
    val spacing: Int,
    val isMainAxisBounded: Boolean = true,
)

internal class StackPlacementSpec(
    val axis: StackAxis,
    val contentRect: Rect,
    val mainAxisPositions: IntArray,
    val mainAxisTranslation: Int = 0,
    val crossAxisOffset: (LayoutNode, Int) -> Int,
)
