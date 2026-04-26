package io.github.fopwoc.mods.framework.ui.compose.layout.core

import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawHostedButton
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawHostedCheckbox
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawHostedSlider
import io.github.fopwoc.mods.framework.ui.compose.layout.hosted.drawHostedTextField
import io.github.fopwoc.mods.framework.ui.compose.layout.list.drawSelectableListElement
import io.github.fopwoc.mods.framework.ui.compose.layout.render.RenderContext
import io.github.fopwoc.mods.framework.ui.compose.layout.render.drawContainer
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.ScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.drawScrollableStackElement
import io.github.fopwoc.mods.framework.ui.compose.layout.scroll.resolveScrollMetrics
import io.github.fopwoc.mods.framework.ui.compose.layout.stack.StackAxis
import io.github.fopwoc.mods.framework.ui.compose.layout.text.drawTextElement
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.horizontalScrollState
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.verticalScrollState
import io.github.fopwoc.mods.framework.ui.compose.node.ComposeTreeNode
import io.github.fopwoc.mods.framework.ui.compose.node.ColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.RowNode
import io.github.fopwoc.mods.framework.ui.compose.node.ScrollableColumnNode
import io.github.fopwoc.mods.framework.ui.compose.node.toLayoutProjection
import io.github.fopwoc.mods.framework.ui.compose.render.HostedElementRenderer
import io.github.fopwoc.mods.framework.ui.compose.render.NoOpHostedElementRenderer

internal class LayoutNode internal constructor(
    element: LayoutElement? = null,
    composeNode: ComposeTreeNode? = null,
    bounds: Rect,
    val children: List<LayoutNode>,
    private var scrollMetrics: ScrollMetrics? = null,
    internal var occupiedSize: Size = Size(bounds.width, bounds.height),
    internal var contentMainAxisSize: Int = 0
) {
    private sealed interface Source {
        data class Legacy(var element: LayoutElement) : Source

        data class Compose(
            var projection: LayoutProjection,
            val shape: LayoutShape = projection.shape
        ) : Source
    }

    private var source: Source = when {
        element != null && composeNode == null -> Source.Legacy(element)
        element == null && composeNode != null -> Source.Compose(composeNode.toLayoutProjection())
        else -> error("LayoutNode requires either a layout element or a compose node source")
    }

    var bounds: Rect = bounds
        internal set

    internal val size: Size
        get() = Size(bounds.width, bounds.height)

    val element: LayoutElement
        get() = when (val current = source) {
            is Source.Legacy -> current.element
            is Source.Compose -> current.projection.toLayoutElement(children.map(LayoutNode::element))
        }

    private val modifier: Modifier
        get() = when (val current = source) {
            is Source.Legacy -> current.element.modifier
            is Source.Compose -> current.projection.modifier
        }

    fun draw(
        context: RenderContext,
        hostedElementRenderer: HostedElementRenderer = NoOpHostedElementRenderer
    ) {
        registerModifierTooltip(context)
        when (val current = element) {
            is LayoutElement.ScrollableColumn -> drawScrollableStackElement(
                context = context,
                bounds = bounds,
                modifier = current.modifier,
                metrics = scrollMetrics,
                drawChildren = {
                    drawChildren(context, hostedElementRenderer)
                }
            )
            is LayoutElement.ScrollableRow -> drawScrollableStackElement(
                context = context,
                bounds = bounds,
                modifier = current.modifier,
                metrics = scrollMetrics,
                drawChildren = {
                    drawChildren(context, hostedElementRenderer)
                }
            )
            else -> {
                drawNode(context, hostedElementRenderer)
                drawChildren(context, hostedElementRenderer)
            }
        }
    }

    private fun drawNode(context: RenderContext, hostedElementRenderer: HostedElementRenderer) {
        when (val current = element) {
            is LayoutElement.Box,
            is LayoutElement.Column,
            is LayoutElement.Row,
            is LayoutElement.Spacer -> drawContainer(context, bounds, current.modifier)
            is LayoutElement.ScrollableColumn,
            is LayoutElement.ScrollableRow -> Unit
            is LayoutElement.Text -> drawTextElement(context, bounds, current)
            is LayoutElement.Button -> drawHostedButton(context, hostedElementRenderer, bounds, current)
            is LayoutElement.Checkbox -> drawHostedCheckbox(context, hostedElementRenderer, bounds, current)
            is LayoutElement.TextField -> drawHostedTextField(context, hostedElementRenderer, bounds, current)
            is LayoutElement.Slider -> drawHostedSlider(context, hostedElementRenderer, bounds, current)
            is LayoutElement.SelectableList -> drawSelectableListElement(context, hostedElementRenderer, bounds, current)
        }
    }

    private fun drawChildren(context: RenderContext, hostedElementRenderer: HostedElementRenderer) {
        children.forEach { child ->
            child.draw(context, hostedElementRenderer)
        }
    }

    private fun registerModifierTooltip(context: RenderContext) {
        val tooltipLines = modifier.tooltipLines
            ?.map { it.formattedString }
            ?.takeIf(List<String>::isNotEmpty)
            ?: return
        if (bounds.width <= 0 || bounds.height <= 0) {
            return
        }

        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.TOOLTIP,
                bounds = bounds,
                tooltipLines = tooltipLines
            )
        )
    }

    internal fun isLayoutEquivalentTo(updatedNode: ComposeTreeNode): Boolean {
        return when (val current = source) {
            is Source.Legacy -> current.element.isLayoutEquivalentTo(updatedNode)
            is Source.Compose -> {
                current.shape == updatedNode.toLayoutShape() &&
                    children.size == updatedNode.children.size &&
                    children.indices.all { index ->
                        children[index].isLayoutEquivalentTo(updatedNode.children[index])
                    }
            }
        }
    }

    internal fun updateFromNode(updatedNode: ComposeTreeNode) {
        require(isLayoutEquivalentTo(updatedNode)) {
            "Cannot refresh LayoutNode with a non-equivalent compose tree"
        }
        require(updatedNode.children.size == children.size) {
            "Equivalent compose trees must preserve child counts"
        }

        children.indices.forEach { index ->
            children[index].updateFromNode(updatedNode.children[index])
        }

        source = Source.Compose(updatedNode.toLayoutProjection())
        scrollMetrics = updatedNode.refreshedScrollMetrics(previous = scrollMetrics, bounds = bounds)
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

private fun ComposeTreeNode.refreshedScrollMetrics(previous: ScrollMetrics?, bounds: Rect): ScrollMetrics? {
    val scrollStateAndAxis = when (this) {
        is ScrollableColumnNode -> state to StackAxis.VERTICAL
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
            axis = axis
        )
    }
}


