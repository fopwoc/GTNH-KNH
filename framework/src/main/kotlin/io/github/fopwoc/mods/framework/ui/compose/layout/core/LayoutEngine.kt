package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.layout.box.boxMeasureSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.box.boxPlacementSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.box.measureBox
import io.github.fopwoc.mods.framework.ui.compose.layout.box.placeBoxChildren
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureButtonNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureCheckboxNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureSliderNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.measureTextFieldNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.list.measureSelectableListNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.list.measureSpacerNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollbarGutterWidth
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.resolveScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackMeasureSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.measureStack
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.placeStackChildren
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.stackMeasureSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.stackPlacementSpec
import io.github.fopwoc.mods.framework.ui.compose.layout.text.measureTextNaturalSize
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.measuredSpacing
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.boxMatchesParentWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.horizontalScrollState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedHeight
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.resolvedFixedWidth
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.verticalScrollState
import io.github.fopwoc.mods.framework.ui.compose.node.BoxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ButtonNode
import io.github.fopwoc.mods.framework.ui.compose.node.CheckboxNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.RootNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.SelectableListNode
import io.github.fopwoc.mods.framework.ui.compose.node.SliderNode
import io.github.fopwoc.mods.framework.ui.compose.node.SpacerNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextFieldNode
import io.github.fopwoc.mods.framework.ui.compose.node.TextNode
import io.github.fopwoc.mods.framework.ui.compose.unit.UiTokens
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved


internal object LayoutEngine {
    internal fun layout(
        root: ComposeTreeNode,
        metrics: TextMetrics,
        viewportWidth: Int,
        viewportHeight: Int
    ): LayoutNode {
        return measure(
            node = root,
            metrics = metrics,
            maxWidth = viewportWidth.coerceAtLeast(0),
            maxHeight = viewportHeight.coerceAtLeast(0)
        ).also { place(it, x = 0, y = 0) }
    }

    internal fun layout(
        root: LayoutElement,
        metrics: TextMetrics,
        viewportWidth: Int,
        viewportHeight: Int
    ): LayoutNode {
        return measure(
            element = root,
            metrics = metrics,
            maxWidth = viewportWidth.coerceAtLeast(0),
            maxHeight = viewportHeight.coerceAtLeast(0)
        ).also { place(it, x = 0, y = 0) }
    }

    internal fun refreshPlacement(root: LayoutNode): LayoutNode {
        return place(root, x = root.bounds.x, y = root.bounds.y)
    }

    private fun measure(
        element: LayoutElement,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val clampedMaxWidth = maxWidth.coerceAtLeast(0)
        val clampedMaxHeight = maxHeight.coerceAtLeast(0)
        return when (element) {
            is LayoutElement.Box -> measureBox(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.Column -> measureColumn(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.ScrollableColumn -> measureScrollableColumn(element, metrics, clampedMaxWidth, clampedMaxHeight)
            is LayoutElement.ScrollableRow -> measureScrollableRow(element, metrics, clampedMaxWidth, clampedMaxHeight)
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

    private fun measure(
        node: ComposeTreeNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val clampedMaxWidth = maxWidth.coerceAtLeast(0)
        val clampedMaxHeight = maxHeight.coerceAtLeast(0)
        return when (node) {
            is RootNode -> measureBoxNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is BoxNode -> measureBoxNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is ColumnNode -> {
                if (node.modifier.verticalScrollState != null) {
                    measureScrollableColumnNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
                } else {
                    measureColumnNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
                }
            }

            is ScrollableColumnNode -> measureScrollableColumnNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is RowNode -> {
                if (node.modifier.horizontalScrollState != null) {
                    measureScrollableRowNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
                } else {
                    measureRowNode(node, metrics, clampedMaxWidth, clampedMaxHeight)
                }
            }

            is TextNode -> measureText(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is ButtonNode -> measureButton(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is CheckboxNode -> measureCheckbox(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is TextFieldNode -> measureTextField(node, clampedMaxWidth, clampedMaxHeight)
            is SliderNode -> measureSlider(node, clampedMaxWidth, clampedMaxHeight)
            is SelectableListNode -> measureSelectableList(node, metrics, clampedMaxWidth, clampedMaxHeight)
            is SpacerNode -> measureSpacer(node, clampedMaxWidth, clampedMaxHeight)
        }
    }

    private fun measureBox(
        element: LayoutElement.Box,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        return measureBox(
            spec = element.boxMeasureSpec(maxWidth, maxHeight),
            element = element,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            },
            resolveNodeSize = ::resolveSize
        )
    }

    private fun measureBoxNode(
        node: ComposeTreeNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val modifier = node.modifier
        val padding = modifier.padding
        val innerWidth = availableInnerWidth(modifier, maxWidth)
        val innerHeight = availableInnerHeight(modifier, maxHeight)
        val initiallyMeasuredChildren = node.children.map { child ->
            measure(child, metrics, innerWidth, innerHeight)
        }
        val contentWidth = initiallyMeasuredChildren.maxOfOrNull { child ->
            if (child.element.modifier.boxMatchesParentWidth) 0 else child.size.width
        } ?: 0
        val contentHeight = initiallyMeasuredChildren.maxOfOrNull { child ->
            if (child.element.modifier.boxMatchesParentHeight) 0 else child.size.height
        } ?: 0
        val resolvedSize = resolveSize(
            modifier = modifier,
            naturalWidth = contentWidth + padding.horizontalValue,
            naturalHeight = contentHeight + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        val resolvedInnerWidth = (resolvedSize.width - padding.horizontalValue).coerceAtLeast(0)
        val resolvedInnerHeight = (resolvedSize.height - padding.verticalValue).coerceAtLeast(0)
        val measuredChildren = node.children.zip(initiallyMeasuredChildren).map { (child, initialMeasurement) ->
            val childModifier = child.modifier
            if (!childModifier.boxMatchesParentWidth && !childModifier.boxMatchesParentHeight) {
                initialMeasurement
            } else {
                val remeasuredChild = measure(child, metrics, resolvedInnerWidth, resolvedInnerHeight)
                val resolvedChildSize = Size(
                    width = if (childModifier.boxMatchesParentWidth) resolvedInnerWidth else remeasuredChild.size.width,
                    height = if (childModifier.boxMatchesParentHeight) resolvedInnerHeight else remeasuredChild.size.height
                )
                remeasuredChild.apply {
                    updateMeasuredSize(size = resolvedChildSize, occupiedSize = resolvedChildSize)
                }
            }
        }
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, resolvedSize.width, resolvedSize.height),
            children = measuredChildren,
            occupiedSize = resolvedSize
        )
    }

    private fun measureColumn(
        element: LayoutElement.Column,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = element.modifier.padding
        val stackMeasurement = measureStack(
            spec = element.stackMeasureSpec(maxWidth, maxHeight),
            children = element.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            }
        )
        val size = resolveSize(
            modifier = element.modifier,
            naturalWidth = stackMeasurement.contentCrossAxisSize + padding.horizontalValue,
            naturalHeight = stackMeasurement.contentMainAxisSize + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            element = element,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = stackMeasurement.contentMainAxisSize
        )
    }

    private fun measureColumnNode(
        node: ColumnNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val stackMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.VERTICAL,
                maxWidth = availableInnerWidth(node.modifier, maxWidth),
                maxHeight = availableInnerHeight(node.modifier, maxHeight),
                spacing = node.verticalArrangement.measuredSpacing(node.children.size)
            ),
            children = node.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = ComposeTreeNode::modifier
        )
        val size = resolveSize(
            modifier = node.modifier,
            naturalWidth = stackMeasurement.contentCrossAxisSize + padding.horizontalValue,
            naturalHeight = stackMeasurement.contentMainAxisSize + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = stackMeasurement.contentMainAxisSize
        )
    }

    private fun measureScrollableColumn(
        element: LayoutElement.ScrollableColumn,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
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
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            }
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
                measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                    measure(child, childMetrics, childMaxWidth, childMaxHeight)
                }
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
        val size = resolveSize(
            modifier = element.modifier,
            naturalWidth = contentWidth + padding.horizontalValue + gutterWidth,
            naturalHeight = (contentHeight + padding.verticalValue).coerceAtMost(maxHeight),
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            element = element,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = contentHeight
        )
    }

    private fun measureScrollableColumnNode(
        node: ComposeTreeNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val modifier = node.modifier
        val padding = modifier.padding
        val rawInnerWidth = availableInnerWidth(modifier, maxWidth)
        val innerHeight = availableInnerHeight(modifier, maxHeight)
        val spacing = when (node) {
            is ColumnNode -> node.verticalArrangement.measuredSpacing(node.children.size)
            is ScrollableColumnNode -> node.verticalArrangement.measuredSpacing(node.children.size)
            else -> error("Unsupported scrollable column node type: ${node::class.simpleName}")
        }
        val initialMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.VERTICAL,
                maxWidth = rawInnerWidth,
                maxHeight = innerHeight,
                spacing = spacing,
                isMainAxisBounded = false
            ),
            children = node.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = ComposeTreeNode::modifier
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
                children = node.children,
                metrics = metrics,
                measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                    measure(child, childMetrics, childMaxWidth, childMaxHeight)
                },
                childModifier = ComposeTreeNode::modifier
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
        val size = resolveSize(
            modifier = modifier,
            naturalWidth = contentWidth + padding.horizontalValue + gutterWidth,
            naturalHeight = (contentHeight + padding.verticalValue).coerceAtMost(maxHeight),
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = contentHeight
        )
    }

    private fun measureScrollableRow(
        element: LayoutElement.ScrollableRow,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = element.modifier.padding
        val innerWidth = availableInnerWidth(element.modifier, maxWidth)
        val rawInnerHeight = availableInnerHeight(element.modifier, maxHeight)
        val spacing = element.horizontalArrangement.measuredSpacing(element.children.size)
        val initialMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.HORIZONTAL,
                maxWidth = innerWidth,
                maxHeight = rawInnerHeight,
                spacing = spacing,
                isMainAxisBounded = false
            ),
            children = element.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            }
        )
        val initialContentWidth = initialMeasurement.contentMainAxisSize
        val needsScrollbar = initialContentWidth > innerWidth && rawInnerHeight > 0
        val contentHeightLimit = if (needsScrollbar) {
            (rawInnerHeight - ScrollbarGutterWidth).coerceAtLeast(0)
        } else {
            rawInnerHeight
        }
        val stackMeasurement = if (needsScrollbar) {
            measureStack(
                spec = StackMeasureSpec(
                    axis = StackAxis.HORIZONTAL,
                    maxWidth = innerWidth,
                    maxHeight = contentHeightLimit,
                    spacing = spacing,
                    isMainAxisBounded = false
                ),
                children = element.children,
                metrics = metrics,
                measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                    measure(child, childMetrics, childMaxWidth, childMaxHeight)
                }
            )
        } else {
            initialMeasurement
        }
        val contentWidth = stackMeasurement.contentMainAxisSize
        val contentHeight = stackMeasurement.contentCrossAxisSize
        val gutterHeight = if (contentWidth > innerWidth) {
            ScrollbarGutterWidth.coerceAtMost(rawInnerHeight)
        } else {
            0
        }
        val size = resolveSize(
            modifier = element.modifier,
            naturalWidth = (contentWidth + padding.horizontalValue).coerceAtMost(maxWidth),
            naturalHeight = contentHeight + padding.verticalValue + gutterHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            element = element,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = contentWidth
        )
    }

    private fun measureScrollableRowNode(
        node: RowNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val modifier = node.modifier
        val padding = modifier.padding
        val innerWidth = availableInnerWidth(modifier, maxWidth)
        val rawInnerHeight = availableInnerHeight(modifier, maxHeight)
        val spacing = node.horizontalArrangement.measuredSpacing(node.children.size)
        val initialMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.HORIZONTAL,
                maxWidth = innerWidth,
                maxHeight = rawInnerHeight,
                spacing = spacing,
                isMainAxisBounded = false
            ),
            children = node.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = ComposeTreeNode::modifier
        )
        val initialContentWidth = initialMeasurement.contentMainAxisSize
        val needsScrollbar = initialContentWidth > innerWidth && rawInnerHeight > 0
        val contentHeightLimit = if (needsScrollbar) {
            (rawInnerHeight - ScrollbarGutterWidth).coerceAtLeast(0)
        } else {
            rawInnerHeight
        }
        val stackMeasurement = if (needsScrollbar) {
            measureStack(
                spec = StackMeasureSpec(
                    axis = StackAxis.HORIZONTAL,
                    maxWidth = innerWidth,
                    maxHeight = contentHeightLimit,
                    spacing = spacing,
                    isMainAxisBounded = false
                ),
                children = node.children,
                metrics = metrics,
                measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                    measure(child, childMetrics, childMaxWidth, childMaxHeight)
                },
                childModifier = ComposeTreeNode::modifier
            )
        } else {
            initialMeasurement
        }
        val contentWidth = stackMeasurement.contentMainAxisSize
        val contentHeight = stackMeasurement.contentCrossAxisSize
        val gutterHeight = if (contentWidth > innerWidth) {
            ScrollbarGutterWidth.coerceAtMost(rawInnerHeight)
        } else {
            0
        }
        val size = resolveSize(
            modifier = modifier,
            naturalWidth = (contentWidth + padding.horizontalValue).coerceAtMost(maxWidth),
            naturalHeight = contentHeight + padding.verticalValue + gutterHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size,
            contentMainAxisSize = contentWidth
        )
    }

    private fun measureRow(
        element: LayoutElement.Row,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = element.modifier.padding
        val stackMeasurement = measureStack(
            spec = element.stackMeasureSpec(maxWidth, maxHeight),
            children = element.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            }
        )
        val size = resolveSize(
            modifier = element.modifier,
            naturalWidth = stackMeasurement.contentMainAxisSize + padding.horizontalValue,
            naturalHeight = stackMeasurement.contentCrossAxisSize + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            element = element,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size
        )
    }

    private fun measureRowNode(
        node: RowNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val stackMeasurement = measureStack(
            spec = StackMeasureSpec(
                axis = StackAxis.HORIZONTAL,
                maxWidth = availableInnerWidth(node.modifier, maxWidth),
                maxHeight = availableInnerHeight(node.modifier, maxHeight),
                spacing = node.horizontalArrangement.measuredSpacing(node.children.size)
            ),
            children = node.children,
            metrics = metrics,
            measureChild = { child, childMetrics, childMaxWidth, childMaxHeight ->
                measure(child, childMetrics, childMaxWidth, childMaxHeight)
            },
            childModifier = ComposeTreeNode::modifier
        )
        val size = resolveSize(
            modifier = node.modifier,
            naturalWidth = stackMeasurement.contentMainAxisSize + padding.horizontalValue,
            naturalHeight = stackMeasurement.contentCrossAxisSize + padding.verticalValue,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, size.width, size.height),
            children = stackMeasurement.children,
            occupiedSize = size
        )
    }

    private fun measureText(
        element: LayoutElement.Text,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureTextNaturalSize(element, metrics, maxWidth)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureText(
        node: TextNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureTextNaturalSize(
            modifier = node.modifier,
            text = node.text,
            style = node.style,
            metrics = metrics,
            maxWidth = maxWidth
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureButton(
        element: LayoutElement.Button,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureButtonNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureButton(
        node: ButtonNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val naturalSize = Size(
            width = kotlin.math.max(98, metrics.textWidth(node.text.formattedString) + 20 + padding.horizontalValue),
            height = kotlin.math.max(20, metrics.lineHeight + 10 + padding.verticalValue)
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureCheckbox(
        element: LayoutElement.Checkbox,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureCheckboxNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureCheckbox(
        node: CheckboxNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val naturalSize = Size(
            width = kotlin.math.max(11, metrics.textWidth(node.label.formattedString) + 13 + padding.horizontalValue),
            height = kotlin.math.max(11, kotlin.math.max(metrics.lineHeight, 11) + padding.verticalValue)
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureTextField(
        element: LayoutElement.TextField,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureTextFieldNaturalSize(element)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureTextField(
        node: TextFieldNode,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val naturalSize = Size(
            width = kotlin.math.max(98, 120 + padding.horizontalValue),
            height = UiTokens.ControlHeight.resolved + padding.verticalValue
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSlider(
        element: LayoutElement.Slider,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureSliderNaturalSize(element)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSlider(
        node: SliderNode,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val naturalSize = Size(
            width = kotlin.math.max(150, 150 + padding.horizontalValue),
            height = UiTokens.ControlHeight.resolved + padding.verticalValue
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSelectableList(
        element: LayoutElement.SelectableList,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureSelectableListNaturalSize(element, metrics)
        return measureLeaf(element, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSelectableList(
        node: SelectableListNode,
        metrics: TextMetrics,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val padding = node.modifier.padding
        val widestItemWidth = node.items.maxOfOrNull(metrics::textWidth) ?: 0
        val rowHeight = node.rowHeight.resolved
        val visibleRows = node.visibleRowCount.coerceAtLeast(1)
        val naturalSize = Size(
            width = kotlin.math.max(120, widestItemWidth + 20 + padding.horizontalValue),
            height = kotlin.math.max(
                rowHeight + padding.verticalValue,
                visibleRows * rowHeight + 8 + padding.verticalValue
            )
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun measureSpacer(
        element: LayoutElement.Spacer,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = measureSpacerNaturalSize(element)
        return measureLeaf(
            element = element,
            naturalWidth = naturalSize.width,
            naturalHeight = naturalSize.height,
            maxWidth = maxWidth,
            maxHeight = maxHeight
        )
    }

    private fun measureSpacer(
        node: SpacerNode,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val naturalSize = Size(
            width = node.modifier.resolvedFixedWidth ?: 0,
            height = node.modifier.resolvedFixedHeight ?: 0
        )
        return measureLeaf(node, naturalSize.width, naturalSize.height, maxWidth, maxHeight)
    }

    private fun place(
        measured: LayoutNode,
        x: Int,
        y: Int
    ): LayoutNode {
        measured.placeAt(x, y)
        val bounds = measured.bounds
        val scrollMetrics = when (val element = measured.element) {
            is LayoutElement.Box -> {
                placeBoxChildren(measured.children, bounds, element)
                null
            }
            is LayoutElement.Column -> {
                placeColumnChildren(measured.children, bounds, element)
                null
            }
            is LayoutElement.ScrollableColumn -> {
                val metrics = resolveScrollMetrics(
                    bounds = bounds,
                    modifier = element.modifier,
                    contentMainAxisSize = measured.contentMainAxisSize,
                    state = element.state,
                    axis = StackAxis.VERTICAL
                )
                placeScrollableColumnChildren(measured.children, element, metrics)
                metrics
            }
            is LayoutElement.ScrollableRow -> {
                val metrics = resolveScrollMetrics(
                    bounds = bounds,
                    modifier = element.modifier,
                    contentMainAxisSize = measured.contentMainAxisSize,
                    state = element.state,
                    axis = StackAxis.HORIZONTAL
                )
                placeScrollableRowChildren(measured.children, element, metrics)
                metrics
            }
            is LayoutElement.Row -> {
                placeRowChildren(measured.children, bounds, element)
                null
            }
            is LayoutElement.Text,
            is LayoutElement.Button,
            is LayoutElement.Checkbox,
            is LayoutElement.TextField,
            is LayoutElement.Slider,
            is LayoutElement.SelectableList,
            is LayoutElement.Spacer -> null
        }
        measured.updateScrollMetrics(scrollMetrics)
        return measured
    }

    private fun placeBoxChildren(
        children: List<LayoutNode>,
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
        children: List<LayoutNode>,
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
        children: List<LayoutNode>,
        element: LayoutElement.ScrollableColumn,
        scrollMetrics: ScrollMetrics
    ): List<LayoutNode> {
        return placeStackChildren(
            children = children,
            spec = element.stackPlacementSpec(children, scrollMetrics.viewportBounds),
            placeChild = ::place
        )
    }

    private fun placeScrollableRowChildren(
        children: List<LayoutNode>,
        element: LayoutElement.ScrollableRow,
        scrollMetrics: ScrollMetrics
    ): List<LayoutNode> {
        return placeStackChildren(
            children = children,
            spec = element.stackPlacementSpec(children, scrollMetrics.viewportBounds),
            placeChild = ::place
        )
    }

    private fun placeRowChildren(
        children: List<LayoutNode>,
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
    ): LayoutNode {
        val size = resolveSize(element.modifier, naturalWidth, naturalHeight, maxWidth, maxHeight)
        return LayoutNode(
            element = element,
            bounds = Rect(0, 0, size.width, size.height),
            children = emptyList(),
            occupiedSize = size
        )
    }

    private fun measureLeaf(
        node: ComposeTreeNode,
        naturalWidth: Int,
        naturalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): LayoutNode {
        val size = resolveSize(node.modifier, naturalWidth, naturalHeight, maxWidth, maxHeight)
        return LayoutNode(
            composeNode = node,
            bounds = Rect(0, 0, size.width, size.height),
            children = emptyList(),
            occupiedSize = size
        )
    }

}


