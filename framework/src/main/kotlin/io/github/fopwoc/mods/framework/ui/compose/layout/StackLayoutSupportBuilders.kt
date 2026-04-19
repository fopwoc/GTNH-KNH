package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.arrange
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.measuredSpacing
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.columnAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.rowAlignment

internal fun LayoutElement.Row.stackMeasureSpec(maxWidth: Int, maxHeight: Int): StackMeasureSpec = StackMeasureSpec(
    axis = StackAxis.HORIZONTAL,
    maxWidth = availableInnerWidth(modifier, maxWidth),
    maxHeight = availableInnerHeight(modifier, maxHeight),
    spacing = horizontalArrangement.measuredSpacing(children.size)
)

internal fun LayoutElement.Column.stackMeasureSpec(maxWidth: Int, maxHeight: Int): StackMeasureSpec = StackMeasureSpec(
    axis = StackAxis.VERTICAL,
    maxWidth = availableInnerWidth(modifier, maxWidth),
    maxHeight = availableInnerHeight(modifier, maxHeight),
    spacing = verticalArrangement.measuredSpacing(children.size)
)

internal fun LayoutElement.Row.stackPlacementSpec(children: List<MeasuredNode>, contentRect: Rect): StackPlacementSpec = StackPlacementSpec(
    axis = StackAxis.HORIZONTAL,
    contentRect = contentRect,
    mainAxisPositions = horizontalArrangement.arrange(
        totalSize = contentRect.width,
        childSizes = children.map { it.occupiedSize.width }
    ),
    crossAxisOffset = { child, availableCrossAxisSize ->
        val alignment = child.element.modifier.rowAlignment ?: verticalAlignment
        alignedOffset(
            alignment = alignment,
            available = availableCrossAxisSize,
            childSize = child.size.height
        )
    }
)

internal fun LayoutElement.Column.stackPlacementSpec(children: List<MeasuredNode>, contentRect: Rect): StackPlacementSpec = StackPlacementSpec(
    axis = StackAxis.VERTICAL,
    contentRect = contentRect,
    mainAxisPositions = verticalArrangement.arrange(
        totalSize = contentRect.height,
        childSizes = children.map { it.occupiedSize.height }
    ),
    crossAxisOffset = { child, availableCrossAxisSize ->
        val alignment = child.element.modifier.columnAlignment ?: horizontalAlignment
        alignedOffset(
            alignment = alignment,
            available = availableCrossAxisSize,
            childSize = child.size.width
        )
    }
)

internal fun LayoutElement.ScrollableRow.stackPlacementSpec(children: List<MeasuredNode>, contentRect: Rect): StackPlacementSpec = StackPlacementSpec(
    axis = StackAxis.HORIZONTAL,
    contentRect = contentRect,
    mainAxisPositions = horizontalArrangement.arrange(
        totalSize = contentRect.width,
        childSizes = children.map { it.occupiedSize.width }
    ),
    mainAxisTranslation = -state.value,
    crossAxisOffset = { child, availableCrossAxisSize ->
        val alignment = child.element.modifier.rowAlignment ?: verticalAlignment
        alignedOffset(
            alignment = alignment,
            available = availableCrossAxisSize,
            childSize = child.size.height
        )
    }
)

internal fun LayoutElement.ScrollableColumn.stackPlacementSpec(children: List<MeasuredNode>, contentRect: Rect): StackPlacementSpec = StackPlacementSpec(
    axis = StackAxis.VERTICAL,
    contentRect = contentRect,
    mainAxisPositions = verticalArrangement.arrange(
        totalSize = contentRect.height,
        childSizes = children.map { it.occupiedSize.height }
    ),
    mainAxisTranslation = -state.value,
    crossAxisOffset = { child, availableCrossAxisSize ->
        val alignment = child.element.modifier.columnAlignment ?: horizontalAlignment
        alignedOffset(
            alignment = alignment,
            available = availableCrossAxisSize,
            childSize = child.size.width
        )
    }
)

