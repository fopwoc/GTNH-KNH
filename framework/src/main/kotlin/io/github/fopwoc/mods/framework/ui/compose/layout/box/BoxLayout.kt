package io.github.fopwoc.mods.framework.ui.compose.layout.box

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Size
import io.github.fopwoc.mods.framework.ui.compose.layout.core.alignedOffset
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetX
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetY

internal data class BoxMeasureSpec(
    val maxWidth: Int,
    val maxHeight: Int,
    val innerWidth: Int,
    val innerHeight: Int,
)

internal data class BoxPlacementSpec(
    val contentRect: Rect,
    val contentAlignment: Alignment,
)

internal fun <T> measureBox(
    spec: BoxMeasureSpec,
    element: LayoutElement.Box,
    children: List<T>,
    metrics: TextMetrics,
    measureChild: (T, TextMetrics, Int, Int) -> LayoutNode,
    childModifier: (T) -> Modifier,
    resolveNodeSize: (Modifier, Int, Int, Int, Int) -> Size,
): LayoutNode {
  val padding = element.modifier.padding
  val initiallyMeasuredChildren = children.map { child ->
    measureChild(child, metrics, spec.innerWidth, spec.innerHeight)
  }
  val contentWidth =
      initiallyMeasuredChildren.maxOfOrNull { child ->
        if (child.element.modifier.boxMatchesParentWidth) 0 else child.size.width
      } ?: 0
  val contentHeight =
      initiallyMeasuredChildren.maxOfOrNull { child ->
        if (child.element.modifier.boxMatchesParentHeight) 0 else child.size.height
      } ?: 0
  val resolvedSize =
      resolveNodeSize(
          element.modifier,
          contentWidth + padding.horizontalValue,
          contentHeight + padding.verticalValue,
          spec.maxWidth,
          spec.maxHeight,
      )
  val resolvedInnerWidth = (resolvedSize.width - padding.horizontalValue).coerceAtLeast(0)
  val resolvedInnerHeight = (resolvedSize.height - padding.verticalValue).coerceAtLeast(0)
  val measuredChildren =
      children.zip(initiallyMeasuredChildren).map { (child, initialMeasurement) ->
        val modifier = childModifier(child)
        if (!modifier.boxMatchesParentWidth && !modifier.boxMatchesParentHeight) {
          initialMeasurement
        } else {
          val remeasuredChild =
              measureChild(child, metrics, resolvedInnerWidth, resolvedInnerHeight)
          val resolvedChildSize =
              Size(
                  width =
                      if (modifier.boxMatchesParentWidth) resolvedInnerWidth
                      else remeasuredChild.size.width,
                  height =
                      if (modifier.boxMatchesParentHeight) resolvedInnerHeight
                      else remeasuredChild.size.height,
              )
          remeasuredChild.apply {
            updateMeasuredSize(size = resolvedChildSize, occupiedSize = resolvedChildSize)
          }
        }
      }
  return LayoutNode(
      element = element,
      bounds = Rect(0, 0, resolvedSize.width, resolvedSize.height),
      children = measuredChildren,
      occupiedSize = resolvedSize,
  )
}

internal fun measureBox(
    spec: BoxMeasureSpec,
    element: LayoutElement.Box,
    metrics: TextMetrics,
    measureChild: (LayoutElement, TextMetrics, Int, Int) -> LayoutNode,
    resolveNodeSize: (Modifier, Int, Int, Int, Int) -> Size,
): LayoutNode =
    measureBox(
        spec = spec,
        element = element,
        children = element.children,
        metrics = metrics,
        measureChild = measureChild,
        childModifier = { it.modifier },
        resolveNodeSize = resolveNodeSize,
    )

internal fun placeBoxChildren(
    children: List<LayoutNode>,
    spec: BoxPlacementSpec,
    placeChild: (LayoutNode, Int, Int) -> LayoutNode,
): List<LayoutNode> {
  return children.map { child ->
    val modifier = child.element.modifier
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
