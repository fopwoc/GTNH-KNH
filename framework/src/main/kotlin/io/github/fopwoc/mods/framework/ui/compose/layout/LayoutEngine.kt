package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import kotlin.math.max

private data class MeasuredNode(
    val element: LayoutElement,
    val size: Size,
    val children: List<MeasuredNode>,
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
        val padding = element.modifier.padding
        val innerWidth = availableInnerWidth(element.modifier, maxWidth)
        val innerHeight = availableInnerHeight(element.modifier, maxHeight)
        val measuredChildren = element.children.map { child ->
            measure(child, metrics, innerWidth, innerHeight)
        }
        val contentWidth = maxChildWidth(measuredChildren)
        val contentHeight = maxChildHeight(measuredChildren)
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = contentWidth + padding.horizontal,
                naturalHeight = contentHeight + padding.vertical,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = measuredChildren
        )
    }

    private fun measureColumn(
        element: LayoutElement.Column,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val innerWidth = availableInnerWidth(element.modifier, maxWidth)
        val innerHeight = availableInnerHeight(element.modifier, maxHeight)
        val measuredChildren = element.children.map { child ->
            measure(child, metrics, innerWidth, innerHeight)
        }
        val contentWidth = maxChildWidth(measuredChildren)
        val contentHeight = totalStackHeight(measuredChildren, element.spacing)
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = contentWidth + padding.horizontal,
                naturalHeight = contentHeight + padding.vertical,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = measuredChildren,
            contentHeight = contentHeight
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
        val initiallyMeasuredChildren = element.children.map { child ->
            measure(child, metrics, rawInnerWidth, innerHeight)
        }
        val initialContentHeight = totalStackHeight(initiallyMeasuredChildren, element.spacing)
        val needsScrollbar = initialContentHeight > innerHeight && rawInnerWidth > 0
        val contentWidthLimit = if (needsScrollbar) {
            (rawInnerWidth - ScrollbarGutterWidth).coerceAtLeast(0)
        } else {
            rawInnerWidth
        }
        val measuredChildren = if (needsScrollbar) {
            element.children.map { child ->
                measure(child, metrics, contentWidthLimit, innerHeight)
            }
        } else {
            initiallyMeasuredChildren
        }
        val contentWidth = maxChildWidth(measuredChildren)
        val contentHeight = totalStackHeight(measuredChildren, element.spacing)
        val gutterWidth = if (contentHeight > innerHeight) {
            ScrollbarGutterWidth.coerceAtMost(rawInnerWidth)
        } else {
            0
        }
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = contentWidth + padding.horizontal + gutterWidth,
                naturalHeight = (contentHeight + padding.vertical).coerceAtMost(maxHeight),
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = measuredChildren,
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
        val innerWidth = availableInnerWidth(element.modifier, maxWidth)
        val innerHeight = availableInnerHeight(element.modifier, maxHeight)
        val measuredChildren = element.children.map { child ->
            measure(child, metrics, innerWidth, innerHeight)
        }
        val contentWidth = totalStackWidth(measuredChildren, element.spacing)
        val contentHeight = maxChildHeight(measuredChildren)
        return MeasuredNode(
            element = element,
            size = resolveSize(
                modifier = element.modifier,
                naturalWidth = contentWidth + padding.horizontal,
                naturalHeight = contentHeight + padding.vertical,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            ),
            children = measuredChildren
        )
    }

    private fun measureText(
        element: LayoutElement.Text,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val contentWidthLimit = availableInnerWidth(element.modifier, maxWidth)
        val lines = if (element.style.wrap && contentWidthLimit > 0) {
            metrics.wrapText(element.text, contentWidthLimit).ifEmpty { listOf("") }
        } else {
            listOf(element.text)
        }
        val widestLineWidth = lines.maxOfOrNull(metrics::textWidth) ?: 0
        val naturalWidth = when {
            element.modifier.fixedWidth != null || element.modifier.fillMaxWidth -> contentWidthLimit + padding.horizontal
            else -> widestLineWidth + padding.horizontal
        }
        val naturalHeight = max(1, lines.size) * metrics.lineHeight + padding.vertical
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureButton(
        element: LayoutElement.Button,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val naturalWidth = max(98, metrics.textWidth(element.text) + 20 + padding.horizontal)
        val naturalHeight = max(20, metrics.lineHeight + 10 + padding.vertical)
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureCheckbox(
        element: LayoutElement.Checkbox,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val naturalWidth = max(11, metrics.textWidth(element.label) + 13 + padding.horizontal)
        val naturalHeight = max(11, max(metrics.lineHeight, 11) + padding.vertical)
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureTextField(
        element: LayoutElement.TextField,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val naturalWidth = max(98, 120 + padding.horizontal)
        val naturalHeight = max(20, 20 + padding.vertical)
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureSlider(
        element: LayoutElement.Slider,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val naturalWidth = max(150, 150 + padding.horizontal)
        val naturalHeight = max(20, 20 + padding.vertical)
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureSelectableList(
        element: LayoutElement.SelectableList,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        val padding = element.modifier.padding
        val widestItemWidth = element.items.maxOfOrNull(metrics::textWidth) ?: 0
        val naturalWidth = max(120, widestItemWidth + 20 + padding.horizontal)
        val naturalHeight = max(
            element.rowHeight + padding.vertical,
            element.visibleRowCount.coerceAtLeast(1) * element.rowHeight + 8 + padding.vertical
        )
        return measureLeaf(element, naturalWidth, naturalHeight, maxWidth, maxHeight)
    }

    private fun measureSpacer(
        element: LayoutElement.Spacer,
        maxWidth: Int,
        maxHeight: Int
    ): MeasuredNode {
        return measureLeaf(
            element = element,
            naturalWidth = element.modifier.fixedWidth ?: 0,
            naturalHeight = element.modifier.fixedHeight ?: 0,
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
                val metrics = createScrollMetrics(bounds, measured, element)
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
        val contentRect = bounds.inset(element.modifier.padding)
        return children.map { child ->
            val modifier = child.element.modifier
            val childX = contentRect.x + alignedOffset(
                alignment = modifier.alignHorizontal,
                available = contentRect.width,
                childSize = child.size.width
            ) + modifier.offsetX
            val childY = contentRect.y + alignedOffset(
                alignment = modifier.alignVertical,
                available = contentRect.height,
                childSize = child.size.height
            ) + modifier.offsetY
            place(child, childX, childY)
        }
    }

    private fun placeColumnChildren(
        children: List<MeasuredNode>,
        bounds: Rect,
        element: LayoutElement.Column
    ): List<LayoutNode> {
        val contentRect = bounds.inset(element.modifier.padding)
        val placedChildren = mutableListOf<LayoutNode>()
        var currentY = contentRect.y
        children.forEachIndexed { index, child ->
            val childX = contentRect.x + alignedOffset(
                alignment = element.horizontalAlignment,
                available = contentRect.width,
                childSize = child.size.width
            ) + child.element.modifier.offsetX
            placedChildren += place(child, childX, currentY + child.element.modifier.offsetY)
            currentY += child.size.height
            if (index < children.lastIndex) {
                currentY += element.spacing
            }
        }
        return placedChildren
    }

    private fun placeScrollableColumnChildren(
        children: List<MeasuredNode>,
        element: LayoutElement.ScrollableColumn,
        scrollMetrics: ScrollMetrics
    ): List<LayoutNode> {
        val contentRect = scrollMetrics.viewport
        val placedChildren = mutableListOf<LayoutNode>()
        var currentY = contentRect.y - element.state.value
        children.forEachIndexed { index, child ->
            val childX = contentRect.x + alignedOffset(
                alignment = element.horizontalAlignment,
                available = contentRect.width,
                childSize = child.size.width
            ) + child.element.modifier.offsetX
            placedChildren += place(child, childX, currentY + child.element.modifier.offsetY)
            currentY += child.size.height
            if (index < children.lastIndex) {
                currentY += element.spacing
            }
        }
        return placedChildren
    }

    private fun placeRowChildren(
        children: List<MeasuredNode>,
        bounds: Rect,
        element: LayoutElement.Row
    ): List<LayoutNode> {
        val contentRect = bounds.inset(element.modifier.padding)
        val placedChildren = mutableListOf<LayoutNode>()
        var currentX = contentRect.x
        children.forEachIndexed { index, child ->
            val childY = contentRect.y + alignedOffset(
                alignment = element.verticalAlignment,
                available = contentRect.height,
                childSize = child.size.height
            ) + child.element.modifier.offsetY
            placedChildren += place(child, currentX + child.element.modifier.offsetX, childY)
            currentX += child.size.width
            if (index < children.lastIndex) {
                currentX += element.spacing
            }
        }
        return placedChildren
    }

    private fun createScrollMetrics(
        bounds: Rect,
        measured: MeasuredNode,
        element: LayoutElement.ScrollableColumn
    ): ScrollMetrics {
        val scrollArea = bounds.inset(element.modifier.padding)
        val gutterWidth = if (measured.contentHeight > scrollArea.height) {
            ScrollbarGutterWidth.coerceAtMost(scrollArea.width)
        } else {
            0
        }
        val viewport = Rect(
            x = scrollArea.x,
            y = scrollArea.y,
            width = (scrollArea.width - gutterWidth).coerceAtLeast(0),
            height = scrollArea.height
        )
        val trackBounds = if (gutterWidth > 0 && scrollArea.height > 0) {
            Rect(
                x = viewport.x + viewport.width + ScrollbarTrackInset,
                y = scrollArea.y,
                width = ScrollbarTrackWidth.coerceAtMost(gutterWidth),
                height = scrollArea.height
            )
        } else {
            null
        }
        element.state.updateMaxValue(measured.contentHeight - viewport.height)
        return ScrollMetrics(
            scrollArea = scrollArea,
            viewport = viewport,
            trackBounds = trackBounds,
            contentHeight = measured.contentHeight,
            state = element.state
        )
    }

    private fun alignedOffset(
        alignment: HorizontalAlignment,
        available: Int,
        childSize: Int
    ): Int {
        return when (alignment) {
            HorizontalAlignment.START -> 0
            HorizontalAlignment.CENTER -> ((available - childSize) / 2).coerceAtLeast(0)
            HorizontalAlignment.END -> (available - childSize).coerceAtLeast(0)
        }
    }

    private fun resolveSize(
        modifier: Modifier,
        naturalWidth: Int,
        naturalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Size {
        return Size(
            width = resolveWidth(modifier, naturalWidth, maxWidth),
            height = resolveHeight(modifier, naturalHeight, maxHeight)
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

    private fun maxChildWidth(children: List<MeasuredNode>): Int = children.maxOfOrNull { it.size.width } ?: 0

    private fun maxChildHeight(children: List<MeasuredNode>): Int = children.maxOfOrNull { it.size.height } ?: 0

    private fun totalStackHeight(children: List<MeasuredNode>, spacing: Int): Int {
        return children.sumOf { it.size.height } + spacingExtent(children.size, spacing)
    }

    private fun totalStackWidth(children: List<MeasuredNode>, spacing: Int): Int {
        return children.sumOf { it.size.width } + spacingExtent(children.size, spacing)
    }

    private fun spacingExtent(childCount: Int, spacing: Int): Int {
        return if (childCount > 1) spacing * (childCount - 1) else 0
    }

    private fun alignedOffset(
        alignment: VerticalAlignment,
        available: Int,
        childSize: Int
    ): Int {
        return when (alignment) {
            VerticalAlignment.TOP -> 0
            VerticalAlignment.CENTER -> ((available - childSize) / 2).coerceAtLeast(0)
            VerticalAlignment.BOTTOM -> (available - childSize).coerceAtLeast(0)
        }
    }

    private fun resolveWidth(modifier: Modifier, naturalWidth: Int, maxWidth: Int): Int {
        return when {
            modifier.fixedWidth != null -> modifier.fixedWidth.coerceAtMost(maxWidth)
            modifier.fillMaxWidth -> maxWidth
            else -> naturalWidth.coerceAtMost(maxWidth)
        }.coerceAtLeast(0)
    }

    private fun availableInnerWidth(modifier: Modifier, maxWidth: Int): Int {
        val containerWidth = when {
            modifier.fixedWidth != null -> modifier.fixedWidth.coerceAtMost(maxWidth)
            modifier.fillMaxWidth -> maxWidth
            else -> maxWidth
        }
        return (containerWidth - modifier.padding.horizontal).coerceAtLeast(0)
    }

    private fun resolveHeight(modifier: Modifier, naturalHeight: Int, maxHeight: Int): Int {
        return when {
            modifier.fixedHeight != null -> modifier.fixedHeight.coerceAtMost(maxHeight)
            modifier.fillMaxHeight -> maxHeight
            else -> naturalHeight.coerceAtMost(maxHeight)
        }.coerceAtLeast(0)
    }

    private fun availableInnerHeight(modifier: Modifier, maxHeight: Int): Int {
        val containerHeight = when {
            modifier.fixedHeight != null -> modifier.fixedHeight.coerceAtMost(maxHeight)
            modifier.fillMaxHeight -> maxHeight
            else -> maxHeight
        }
        return (containerHeight - modifier.padding.vertical).coerceAtLeast(0)
    }
}


