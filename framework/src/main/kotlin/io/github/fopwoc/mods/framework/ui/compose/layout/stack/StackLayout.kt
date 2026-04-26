package io.github.fopwoc.mods.framework.ui.compose.layout.stack

import io.github.fopwoc.mods.framework.ui.compose.layout.core.LayoutNode
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Size
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnWeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetX
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedOffsetY
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowFill
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowWeight

internal enum class StackAxis {
    HORIZONTAL,
    VERTICAL
}

internal data class StackMeasurement(
    val children: List<LayoutNode>,
    val contentMainAxisSize: Int,
    val contentCrossAxisSize: Int
)

internal data class StackMeasureSpec(
    val axis: StackAxis,
    val maxWidth: Int,
    val maxHeight: Int,
    val spacing: Int,
    val isMainAxisBounded: Boolean = true
)

internal class StackPlacementSpec(
    val axis: StackAxis,
    val contentRect: Rect,
    val mainAxisPositions: IntArray,
    val mainAxisTranslation: Int = 0,
    val crossAxisOffset: (LayoutNode, Int) -> Int
)

internal fun <T> measureStack(
    spec: StackMeasureSpec,
    children: List<T>,
    metrics: TextMetrics,
    measureChild: (T, TextMetrics, Int, Int) -> LayoutNode,
    childModifier: (T) -> Modifier,
): StackMeasurement {
    val measuredChildren = when (spec.axis) {
        StackAxis.HORIZONTAL -> measureWeightedRowChildren(
            children = children,
            metrics = metrics,
            maxWidth = spec.maxWidth,
            maxHeight = spec.maxHeight,
            spacing = spec.spacing,
            isMainAxisBounded = spec.isMainAxisBounded,
            measureChild = measureChild,
            childModifier = childModifier
        )
        StackAxis.VERTICAL -> measureWeightedColumnChildren(
            children = children,
            metrics = metrics,
            maxWidth = spec.maxWidth,
            maxHeight = spec.maxHeight,
            spacing = spec.spacing,
            isMainAxisBounded = spec.isMainAxisBounded,
            measureChild = measureChild,
            childModifier = childModifier
        )
    }
    return StackMeasurement(
        children = measuredChildren,
        contentMainAxisSize = measuredChildren.totalStackSize(spec.axis, spec.spacing),
        contentCrossAxisSize = measuredChildren.maxCrossAxisSize(spec.axis)
    )
}

internal fun measureStack(
    spec: StackMeasureSpec,
    children: List<LayoutElement>,
    metrics: TextMetrics,
    measureChild: (LayoutElement, TextMetrics, Int, Int) -> LayoutNode,
): StackMeasurement = measureStack(
    spec = spec,
    children = children,
    metrics = metrics,
    measureChild = measureChild,
    childModifier = { it.modifier }
)

internal fun placeStackChildren(
    children: List<LayoutNode>,
    spec: StackPlacementSpec,
    placeChild: (LayoutNode, Int, Int) -> LayoutNode
): List<LayoutNode> {
    return children.mapIndexed { index, child ->
        val modifier = child.element.modifier
        val mainAxisPosition = spec.mainAxisPositions[index] + spec.mainAxisTranslation + modifier.mainAxisOffset(spec.axis)
        val crossAxisPosition = spec.crossAxisOffset(child, spec.contentRect.crossAxisSize(spec.axis)) + modifier.crossAxisOffset(spec.axis)
        val childX = when (spec.axis) {
            StackAxis.HORIZONTAL -> spec.contentRect.x + mainAxisPosition
            StackAxis.VERTICAL -> spec.contentRect.x + crossAxisPosition
        }
        val childY = when (spec.axis) {
            StackAxis.HORIZONTAL -> spec.contentRect.y + crossAxisPosition
            StackAxis.VERTICAL -> spec.contentRect.y + mainAxisPosition
        }
        placeChild(child, childX, childY)
    }
}

private fun <T> measureWeightedRowChildren(
    children: List<T>,
    metrics: TextMetrics,
    maxWidth: Int,
    maxHeight: Int,
    spacing: Int,
    isMainAxisBounded: Boolean = true,
    measureChild: (T, TextMetrics, Int, Int) -> LayoutNode,
    childModifier: (T) -> Modifier,
): List<LayoutNode> {
    return measureWeightedStackChildren(
        children = children,
        metrics = metrics,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        spacing = spacing,
        isMainAxisBounded = isMainAxisBounded,
        axis = StackAxis.HORIZONTAL,
        measureChild = measureChild,
        childModifier = childModifier
    )
}

private fun <T> measureWeightedColumnChildren(
    children: List<T>,
    metrics: TextMetrics,
    maxWidth: Int,
    maxHeight: Int,
    spacing: Int,
    isMainAxisBounded: Boolean = true,
    measureChild: (T, TextMetrics, Int, Int) -> LayoutNode,
    childModifier: (T) -> Modifier,
): List<LayoutNode> {
    return measureWeightedStackChildren(
        children = children,
        metrics = metrics,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        spacing = spacing,
        isMainAxisBounded = isMainAxisBounded,
        axis = StackAxis.VERTICAL,
        measureChild = measureChild,
        childModifier = childModifier
    )
}

private fun <T> measureWeightedStackChildren(
    children: List<T>,
    metrics: TextMetrics,
    maxWidth: Int,
    maxHeight: Int,
    spacing: Int,
    isMainAxisBounded: Boolean,
    axis: StackAxis,
    measureChild: (T, TextMetrics, Int, Int) -> LayoutNode,
    childModifier: (T) -> Modifier,
): List<LayoutNode> {
    if (!isMainAxisBounded) {
        return children.map { child ->
            measureChild(child, metrics, maxWidth, maxHeight)
        }
    }

    val measuredChildren = arrayOfNulls<LayoutNode>(children.size)
    val weightedIndexes = mutableListOf<Int>()
    var occupiedFixedMainAxisSize = 0

    children.forEachIndexed { index, child ->
        if (childModifier(child).stackWeight(axis) != null) {
            weightedIndexes += index
        } else {
            val measuredChild = measureChild(child, metrics, maxWidth, maxHeight)
            measuredChildren[index] = measuredChild
            occupiedFixedMainAxisSize += measuredChild.occupiedSize.mainAxisSize(axis)
        }
    }

    val availableMainAxisSize = when (axis) {
        StackAxis.HORIZONTAL -> maxWidth
        StackAxis.VERTICAL -> maxHeight
    }
    val remainingMainAxisSize = (availableMainAxisSize - occupiedFixedMainAxisSize - spacingExtent(children.size, spacing)).coerceAtLeast(0)
    val allocatedMainAxisSizes = distributeWeightedSpace(
        totalSpace = remainingMainAxisSize,
        weights = weightedIndexes.map { childModifier(children[it]).stackWeight(axis) ?: 0f }
    )

    weightedIndexes.forEachIndexed { weightedIndex, childIndex ->
        val child = children[childIndex]
        val modifier = childModifier(child)
        val allocatedMainAxisSize = allocatedMainAxisSizes[weightedIndex]
        val measuredChild = when (axis) {
            StackAxis.HORIZONTAL -> measureChild(child, metrics, allocatedMainAxisSize, maxHeight)
            StackAxis.VERTICAL -> measureChild(child, metrics, maxWidth, allocatedMainAxisSize)
        }
        val actualMainAxisSize = if (modifier.stackFill(axis)) {
            allocatedMainAxisSize
        } else {
            measuredChild.size.mainAxisSize(axis).coerceAtMost(allocatedMainAxisSize)
        }
        measuredChildren[childIndex] = measuredChild.apply {
            updateMeasuredSize(
                size = when (axis) {
                    StackAxis.HORIZONTAL -> Size(width = actualMainAxisSize, height = measuredChild.size.height)
                    StackAxis.VERTICAL -> Size(width = measuredChild.size.width, height = actualMainAxisSize)
                },
                occupiedSize = when (axis) {
                    StackAxis.HORIZONTAL -> Size(width = allocatedMainAxisSize, height = measuredChild.size.height)
                    StackAxis.VERTICAL -> Size(width = measuredChild.size.width, height = allocatedMainAxisSize)
                }
            )
        }
    }

    return measuredChildren.requireNoNulls().toList()
}

private fun Modifier.stackWeight(axis: StackAxis): Float? {
    return when (axis) {
        StackAxis.HORIZONTAL -> rowWeight
        StackAxis.VERTICAL -> columnWeight
    }
}

private fun Modifier.stackFill(axis: StackAxis): Boolean {
    return when (axis) {
        StackAxis.HORIZONTAL -> rowFill
        StackAxis.VERTICAL -> columnFill
    }
}

private fun Size.mainAxisSize(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> width
        StackAxis.VERTICAL -> height
    }
}

private fun Size.crossAxisSize(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> height
        StackAxis.VERTICAL -> width
    }
}

private fun Rect.crossAxisSize(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> height
        StackAxis.VERTICAL -> width
    }
}

private fun Modifier.mainAxisOffset(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> resolvedOffsetX
        StackAxis.VERTICAL -> resolvedOffsetY
    }
}

private fun Modifier.crossAxisOffset(axis: StackAxis): Int {
    return when (axis) {
        StackAxis.HORIZONTAL -> resolvedOffsetY
        StackAxis.VERTICAL -> resolvedOffsetX
    }
}


private fun distributeWeightedSpace(totalSpace: Int, weights: List<Float>): IntArray {
    if (weights.isEmpty() || totalSpace <= 0) {
        return IntArray(weights.size)
    }

    val allocations = IntArray(weights.size)
    var remainingSpace = totalSpace
    var remainingWeight = weights.sum().toDouble()
    weights.forEachIndexed { index, weight ->
        val allocation = if (index == weights.lastIndex || remainingWeight <= 0.0) {
            remainingSpace
        } else {
            ((remainingSpace.toDouble() * weight.toDouble()) / remainingWeight).toInt().coerceIn(0, remainingSpace)
        }
        allocations[index] = allocation
        remainingSpace -= allocation
        remainingWeight -= weight.toDouble()
    }
    return allocations
}

private fun List<LayoutNode>.maxCrossAxisSize(axis: StackAxis): Int = maxOfOrNull { it.size.crossAxisSize(axis) } ?: 0

private fun List<LayoutNode>.totalStackSize(axis: StackAxis, spacing: Int): Int {
    return sumOf { it.occupiedSize.mainAxisSize(axis) } + spacingExtent(size, spacing)
}

private fun spacingExtent(childCount: Int, spacing: Int): Int {
    return if (childCount > 1) spacing * (childCount - 1) else 0
}

