package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.measuredSpacing
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal data class MeasuredNode(
    val element: LayoutElement,
    val size: Size,
    val children: List<MeasuredNode>,
    val occupiedSize: Size = size,
    val contentHeight: Int = 0
)


object LayoutEngine {
    fun layout(
        root: LayoutElement,
        metrics: TextMetrics,
        viewportWidth: Int,
        viewportHeight: Int
    ): LayoutNode {
        val measured = measure(
            element = root,
            metrics = metrics,
            maxWidth = viewportWidth.coerceAtLeast(0),
            maxHeight = viewportHeight.coerceAtLeast(0)
        )
        return place(measured, x = 0, y = 0)
    }

    private fun measure(
        element: LayoutElement,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val clampedMaxWidth = maxWidth.coerceAtLeast(0)
        val clampedMaxHeight = maxHeight.coerceAtLeast(0)
        return when (element) {
            is LayoutElement.Box -> measureBox(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Column -> measureColumn(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.ScrollableColumn -> measureScrollableColumn(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Row -> measureRow(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Text -> measureText(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Button -> measureButton(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Checkbox -> measureCheckbox(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.TextField -> measureTextField(element, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Slider -> measureSlider(element, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.SelectableList -> measureSelectableList(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Spacer -> measureSpacer(element, clampedMaxWidth, clampedMaxHeight)
        }
    }

    private fun measureBox(
        element: LayoutElement.Box,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        return measureBox(
            spec = element.boxMeasureSpec(maxWidth, maxHeight),
            element = element,
            metrics = metrics,
            measureChild = ::measure,
            resolveNodeSize = ::resolveSize
        )
    }

    private fun measureColumn(
        element: LayoutElement.Column,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val stackMeasurement = measureStack(
            spec = element.stackMeasureSpec(maxWidth, maxHeight),
            children = element.children,
            metrics = metrics,
            measureChild = ::measure
        )
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = stackMeasurement.contentCrossAxisSize + padding.horizontalValue,
                naturalHeight = stackMeasurement.contentMainAxisSize + padding.verticalValue,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = stackMeasurement.children,
            contentHeight = stackMeasurement.contentMainAxisSize
        )
    }

    private fun measureScrollableColumn(
        element: LayoutElement.ScrollableColumn,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val rawInnerWidth = availableInnerWidth(element.modifier, maxWidth)
        val innerHeight = availableInnerHeight(element.modifier, maxHeight)
        val spacing = element.verticalArrangement.measuredSpacing(element.children.size)
        val initialMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.VERTICAL,
                maxWidth = rawInnerWidth,
                maxHeight = innerHeight,
                spacing = spacing,
                isMainAxisBounded = false
            ),
            children = element.children,
            metrics = metrics,
            measureChild = ::measure
        )
        val initialContentHeight = initialMeasurement.contentMainAxisSize
        val needsScrollbar = initialContentHeight > innerHeight && rawInnerWidth > 0
        val contentWidthLimit = if (needsScrollbar) {
            (rawInnerWidth - ScrollbarGutterWidth).coerceAtLeast(0)
        } else {
            rawInnerWidth
        }
        val stackMeasurement = if (needsScrollbar) {
            measureStack(
                spec = StackMeasureSpec(
                    axis = StackAxis.VERTICAL,
                    maxWidth = contentWidthLimit,
                    maxHeight = innerHeight,
                    spacing = spacing,
                    isMainAxisBounded = false
                ),
                children = element.children,
                metrics = metrics,
                measureChild = ::measure
            )
        } else {
            initialMeasurement
        }
        val contentWidth = stackMeasurement.contentCrossAxisSize
        val contentHeight = stackMeasurement.contentMainAxisSize
        val gutterWidth = if (contentHeight > innerHeight) {
            ScrollbarGutterWidth.coerceAtMost(rawInnerWidth)
        } else {
            0
        }
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = contentWidth + padding.horizontalValue + gutterWidth,
                naturalHeight = (contentHeight + padding.verticalValue).coerceAtMost(maxHeight),
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = stackMeasurement.children,
            contentHeight = contentHeight
        )
    }

    private fun measureRow(
        element: LayoutElement.Row,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val stackMeasurement = measureStack(
            spec = element.stackMeasureSpec(maxWidth, maxHeight),
            children = element.children,
            metrics = metrics,
            measureChild = ::measure
        )
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = stackMeasurement.contentMainAxisSize + padding.horizontalValue,
                naturalHeight = stackMeasurement.contentCrossAxisSize + padding.verticalValue,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = stackMeasurement.children
        )
    }

    private fun measureText(
        element: LayoutElement.Text,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureTextNaturalSize(element, metrics, maxWidth)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureButton(
        element: LayoutElement.Button,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureButtonNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureCheckbox(
        element: LayoutElement.Checkbox,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureCheckboxNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureTextField(
        element: LayoutElement.TextField,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureTextFieldNaturalSize(element)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSlider(
        element: LayoutElement.Slider,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureSliderNaturalSize(element)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSelectableList(
        element: LayoutElement.SelectableList,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureSelectableListNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSpacer(
        element: LayoutElement.Spacer,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val naturalSize = measureSpacerNaturalSize(element)
        return measureLeaf(
            element = element,
            naturalWidth = naturalSize.width,
            naturalHeight = naturalSize.height,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
    }

    private fun place(
        measured: MeasuredNode,
        x: Int,
        y: Int
    ): LayoutNode {
        val bounds = Rect(
            x = x,
            y = y,
            width = measured.size.width,
            height = measured.size.height
        )
        val (children, scrollMetrics) = when (val element = measured.element) {
            is LayoutElement.Box -> placeBoxChildren(measured.children, bounds, element) to null
            is LayoutElement.Column -> placeColumnChildren(measured.children, bounds, element) to null
            is LayoutElement.ScrollableColumn -> {
                val metrics = resolveScrollMetrics(
                    bounds = bounds,
                    modifier = element.modifier,
                    contentHeight = measured.contentHeight,
                    state = element.state
                )
                placeScrollableColumnChildren(measured.children, element, metrics) to metrics
            }
            is LayoutElement.Row -> placeRowChildren(measured.children, bounds, element) to null
            is LayoutElement.Text,
            is LayoutElement.Button,
            is LayoutElement.Checkbox,
            is LayoutElement.TextField,
            is LayoutElement.Slider,
            is LayoutElement.SelectableList,
            is LayoutElement.Spacer -> emptyList<LayoutNode>() to null
        }
        return LayoutNode(
            element = measured.element,
            bounds = bounds,
            children = children,
            scrollMetrics = scrollMetrics
        )
    }

    private fun placeBoxChildren(
        children: List<MeasuredNode>,
        bounds: Rect,
        element: LayoutElement.Box
    ): List<LayoutNode> {
        return placeBoxChildren(
            children = children,
            spec = element.boxPlacementSpec(bounds.inset(element.modifier.padding)),
            placeChild = ::place
        )
    }

    private fun placeColumnChildren(
        children: List<MeasuredNode>,
        bounds: Rect,
        element: LayoutElement.Column
    ): List<LayoutNode> {
        return placeStackChildren(
            children = children,
            spec = element.stackPlacementSpec(children, bounds.inset(element.modifier.padding)),
            placeChild = ::place
        )
    }

    private fun placeScrollableColumnChildren(
        children: List<MeasuredNode>,
        element: LayoutElement.ScrollableColumn,
        scrollMetrics: ScrollMetrics
    ): List<LayoutNode> {
        return placeStackChildren(
            children = children,
            spec = element.stackPlacementSpec(children, scrollMetrics.viewportBounds),
            placeChild = ::place
        )
    }

    private fun placeRowChildren(
        children: List<MeasuredNode>,
        bounds: Rect,
        element: LayoutElement.Row
    ): List<LayoutNode> {
        return placeStackChildren(
            children = children,
            spec = element.stackPlacementSpec(children, bounds.inset(element.modifier.padding)),
            placeChild = ::place
        )
    }


    private fun measureLeaf(
        element: LayoutElement,
        naturalWidth: Int,
        naturalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        return MeasuredNode(
            element = element,
            size = resolveSize(element.modifier, naturalWidth, naturalHeight, maxWidth, maxHeight),
            children = emptyList()
        )
    }

}


