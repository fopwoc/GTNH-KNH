package io.github.fopwoc.mods.framework.ui.compose.layout

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
    val innerHeight: Int
)

internal data class BoxPlacementSpec(
    val contentRect: Rect,
    val contentAlignment: Alignment
)

internal fun measureBox(
    spec: BoxMeasureSpec,
    element: LayoutElement.Box,
    metrics: TextMetrics,
    measureChild: (LayoutElement, TextMetrics, Int, Int) -> MeasuredNode,
    resolveNodeSize: (Modifier, Int, Int, Int, Int) -> Size
): MeasuredNode {
    val padding = element.modifier.padding
    val initiallyMeasuredChildren = element.children.map { child ->
        measureChild(child, metrics, spec.innerWidth, spec.innerHeight)
    }
    val contentWidth = initiallyMeasuredChildren.maxOfOrNull { child ->
        if (child.element.modifier.boxMatchesParentWidth) 0 else child.size.width
    } ?: 0
    val contentHeight = initiallyMeasuredChildren.maxOfOrNull { child ->
        if (child.element.modifier.boxMatchesParentHeight) 0 else child.size.height
    } ?: 0
    val resolvedSize = resolveNodeSize(
        element.modifier,
        contentWidth + padding.horizontalValue,
        contentHeight + padding.verticalValue,
        spec.maxWidth,
        spec.maxHeight
    )
    val resolvedInnerWidth = (resolvedSize.width - padding.horizontalValue).coerceAtLeast(0)
    val resolvedInnerHeight = (resolvedSize.height - padding.verticalValue).coerceAtLeast(0)
    val measuredChildren = element.children.zip(initiallyMeasuredChildren).map { (child, initialMeasurement) ->
        val modifier = child.modifier
        if (!modifier.boxMatchesParentWidth && !modifier.boxMatchesParentHeight) {
            initialMeasurement
        } else {
            val remeasuredChild = measureChild(child, metrics, resolvedInnerWidth, resolvedInnerHeight)
            val resolvedChildSize = Size(
                width = if (modifier.boxMatchesParentWidth) resolvedInnerWidth else remeasuredChild.size.width,
                height = if (modifier.boxMatchesParentHeight) resolvedInnerHeight else remeasuredChild.size.height
            )
            remeasuredChild.copy(
                size = resolvedChildSize,
                occupiedSize = resolvedChildSize
            )
        }
    }
    return MeasuredNode(
        element = element,
        size = resolvedSize,
        children = measuredChildren
    )
}

internal fun placeBoxChildren(
    children: List<MeasuredNode>,
    spec: BoxPlacementSpec,
    placeChild: (MeasuredNode, Int, Int) -> LayoutNode
): List<LayoutNode> {
    return children.map { child ->
        val modifier = child.element.modifier
        val alignment = modifier.boxAlignment ?: spec.contentAlignment
        val childX = spec.contentRect.x + alignedOffset(
            alignment = alignment.horizontal,
            available = spec.contentRect.width,
            childSize = child.size.width
        ) + modifier.resolvedOffsetX
        val childY = spec.contentRect.y + alignedOffset(
            alignment = alignment.vertical,
            available = spec.contentRect.height,
            childSize = child.size.height
        ) + modifier.resolvedOffsetY
        placeChild(child, childX, childY)
    }
}


