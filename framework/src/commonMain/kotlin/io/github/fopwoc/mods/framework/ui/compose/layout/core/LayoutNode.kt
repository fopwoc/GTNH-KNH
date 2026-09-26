package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawButton
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawCheckbox
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawSlider
import io.github.fopwoc.mods.framework.ui.compose.layout.list.drawSelectableList
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.drawScrollableStack
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.resolveScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.layout.text.drawStyledText
import io.github.fopwoc.mods.framework.ui.compose.layout.text.drawTextField
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.horizontalScrollState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.verticalScrollState
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeContainerProjection
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeLeafProjection
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.LazyColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.toLayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.state.LazyListState
import io.github.fopwoc.mods.framework.ui.compose.state.ScrollState

/**
 * A measured and placed node of the composed tree. It keeps the node's [projection], what it draws
 * and how it lays out, until composition refreshes it.
 */
internal class LayoutNode
internal constructor(
    composeNode: ComposeTreeNode,
    bounds: Rect,
    val children: List<LayoutNode>,
    private var scrollMetrics: ScrollMetrics? = null,
    internal var occupiedSize: Size = Size(bounds.width, bounds.height),
    internal var contentMainAxisSize: Int = 0,
) {
    var projection: LayoutProjection = composeNode.toLayoutProjection()
        private set

    /** The layout-relevant part of [projection], computed once per refresh. */
    internal var shape: LayoutShape = projection.shape
        private set

    var bounds: Rect = bounds
        internal set

    internal val size: Size
        get() = Size(bounds.width, bounds.height)

    internal val modifier: Modifier
        get() = projection.modifier

    internal val scrollState: ScrollState?
        get() =
            when (val current = projection) {
                is ComposeContainerProjection.Column -> current.scrollState
                is ComposeContainerProjection.Row -> current.scrollState
                is ComposeContainerProjection.LazyColumn -> current.state.scroll
                else -> null
            }

    internal val lazyListState: LazyListState?
        get() = (projection as? ComposeContainerProjection.LazyColumn)?.state

    fun draw(context: RenderContext) {
        registerModifierTooltip(context)
        registerModifierClick(context)
        when (shape) {
            is LayoutShape.ScrollableColumn,
            is LayoutShape.ScrollableRow,
            is LayoutShape.LazyColumn ->
                drawScrollableStack(
                    context = context,
                    bounds = bounds,
                    modifier = modifier,
                    metrics = scrollMetrics,
                    drawChildren = { drawChildren(context) },
                )
            else -> {
                drawNode(context)
                drawChildren(context)
            }
        }
    }

    private fun drawNode(context: RenderContext) {
        when (val current = projection) {
            is ComposeContainerProjection,
            is ComposeLeafProjection.Spacer -> drawContainer(context, bounds, current.modifier)
            is ComposeLeafProjection.Text -> drawStyledText(context, bounds, current)
            is ComposeLeafProjection.Button -> drawButton(context, bounds, current)
            is ComposeLeafProjection.Checkbox -> drawCheckbox(context, bounds, current)
            is ComposeLeafProjection.TextField -> drawTextField(context, bounds, current)
            is ComposeLeafProjection.Slider -> drawSlider(context, bounds, current)
            is ComposeLeafProjection.SelectableList -> drawSelectableList(context, bounds, current)
            is ComposeLeafProjection.GpuCanvas -> {
                drawContainer(context, bounds, current.modifier)
                context.withClipRect(bounds) {
                    context.drawGpuCanvas(bounds, current.state.frame, current.handle)
                }
            }
        }
    }

    private fun drawChildren(context: RenderContext) {
        children.forEach { child ->
            child.draw(context)
        }
    }

    private fun registerModifierTooltip(context: RenderContext) {
        val tooltipLines =
            modifier.tooltipLines?.map { it.formattedString }?.takeIf(List<String>::isNotEmpty)
                ?: return
        if (bounds.width <= 0 || bounds.height <= 0) {
            return
        }

        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.TOOLTIP,
                bounds = bounds,
                tooltipLines = tooltipLines,
            )
        )
    }

    private fun registerModifierClick(context: RenderContext) {
        val onClick = modifier.onClick ?: return
        if (bounds.width <= 0 || bounds.height <= 0) {
            return
        }

        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.CLICKABLE,
                bounds = bounds,
                onPress = { _, _, button ->
                    if (button == 0) {
                        onClick()
                        InputPressResult.Consumed
                    } else {
                        InputPressResult.Ignored
                    }
                },
            )
        )
    }

    /**
     * Whether [updatedNode] lays out like this node's subtree, so a refresh can keep the layout.
     */
    internal fun isLayoutEquivalentTo(updatedNode: ComposeTreeNode): Boolean =
        shape == updatedNode.toLayoutShape() &&
            children.size == updatedNode.children.size &&
            children.indices.all { index ->
                children[index].isLayoutEquivalentTo(updatedNode.children[index])
            }

    internal fun updateFromNode(updatedNode: ComposeTreeNode) {
        require(isLayoutEquivalentTo(updatedNode)) {
            "Cannot refresh LayoutNode with a non-equivalent compose tree"
        }
        children.indices.forEach { index ->
            children[index].updateFromNode(updatedNode.children[index])
        }
        projection = updatedNode.toLayoutProjection()
        shape = projection.shape
        scrollMetrics =
            updatedNode.refreshedScrollMetrics(previous = scrollMetrics, bounds = bounds)
    }

    internal fun updateMeasuredSize(size: Size, occupiedSize: Size = size) {
        bounds = Rect(bounds.x, bounds.y, size.width, size.height)
        this.occupiedSize = occupiedSize
    }

    internal fun placeAt(x: Int, y: Int) {
        bounds = Rect(x, y, bounds.width, bounds.height)
    }

    internal fun updateScrollMetrics(metrics: ScrollMetrics?) {
        scrollMetrics = metrics
    }
}

private fun ComposeTreeNode.refreshedScrollMetrics(
    previous: ScrollMetrics?,
    bounds: Rect,
): ScrollMetrics? {
    val scrollStateAndAxis =
        when (this) {
            is ScrollableColumnNode -> state to StackAxis.VERTICAL
            is LazyColumnNode -> state.scroll to StackAxis.VERTICAL
            is ColumnNode -> modifier.verticalScrollState?.let { it to StackAxis.VERTICAL }
            is RowNode -> modifier.horizontalScrollState?.let { it to StackAxis.HORIZONTAL }
            else -> null
        } ?: return null

    val (state, axis) = scrollStateAndAxis
    return previous?.let { existingMetrics ->
        resolveScrollMetrics(
            bounds = bounds,
            modifier = modifier,
            contentMainAxisSize = existingMetrics.contentMainAxisSize,
            state = state,
            axis = axis,
        )
    }
}
