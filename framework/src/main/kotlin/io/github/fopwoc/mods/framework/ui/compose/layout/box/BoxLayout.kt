package io.github.fopwoc.mods.framework.ui.compose.layout.box

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.core.alignedOffset
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetX
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetY

internal data class BoxPlacementSpec(
    val contentRect: Rect,
    val contentAlignment: Alignment,
)

internal fun placeBoxChildren(
    children: List<LayoutNode>,
    spec: BoxPlacementSpec,
    placeChild: (LayoutNode, Int, Int) -> LayoutNode,
): List<LayoutNode> {
  return children.map { child ->
    val modifier = child.modifier
    val alignment = modifier.boxAlignment ?: spec.contentAlignment
    val childX =
        spec.contentRect.x +
            alignedOffset(
                alignment = alignment.horizontal,
                available = spec.contentRect.width,
                childSize = child.size.width,
            ) +
            modifier.resolvedOffsetX
    val childY =
        spec.contentRect.y +
            alignedOffset(
                alignment = alignment.vertical,
                available = spec.contentRect.height,
                childSize = child.size.height,
            ) +
            modifier.resolvedOffsetY
    placeChild(child, childX, childY)
  }
}
