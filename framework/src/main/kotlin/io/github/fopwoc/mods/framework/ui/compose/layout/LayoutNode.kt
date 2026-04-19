package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

class LayoutNode internal constructor(
    var element: LayoutElement,
    val bounds: Rect,
    val children: List<LayoutNode>,
    private var scrollMetrics: ScrollMetrics? = null
) {
    fun draw(context: RenderContext) {
        registerModifierTooltip(context)
        when (val current = element) {
            is LayoutElement.ScrollableColumn -> drawScrollableStackElement(
                context = context,
                bounds = bounds,
                modifier = current.modifier,
                metrics = scrollMetrics,
                drawChildren = {
                    drawChildren(context)
                }
            )
            is LayoutElement.ScrollableRow -> drawScrollableStackElement(
                context = context,
                bounds = bounds,
                modifier = current.modifier,
                metrics = scrollMetrics,
                drawChildren = {
                    drawChildren(context)
                }
            )
            else -> {
                drawNode(context)
                drawChildren(context)
            }
        }
    }

    private fun drawNode(context: RenderContext) {
        when (val current = element) {
            is LayoutElement.Box,
            is LayoutElement.Column,
            is LayoutElement.Row,
            is LayoutElement.Spacer -> drawContainer(context, bounds, current.modifier)
            is LayoutElement.ScrollableColumn,
            is LayoutElement.ScrollableRow -> Unit
            is LayoutElement.Text -> drawTextElement(context, bounds, current)
            is LayoutElement.Button -> drawHostedButton(context, bounds, current)
            is LayoutElement.Checkbox -> drawHostedCheckbox(context, bounds, current)
            is LayoutElement.TextField -> drawHostedTextField(context, bounds, current)
            is LayoutElement.Slider -> drawHostedSlider(context, bounds, current)
            is LayoutElement.SelectableList -> drawSelectableListElement(context, bounds, current)
        }
    }

    private fun drawChildren(context: RenderContext) {
        children.forEach { child ->
            child.draw(context)
        }
    }

    private fun registerModifierTooltip(context: RenderContext) {
        val tooltipLines = element.modifier.tooltipLines
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

    internal fun updateElements(updatedElement: LayoutElement) {
        require(element.isLayoutEquivalentTo(updatedElement)) {
            "Cannot refresh LayoutNode with a non-equivalent element tree"
        }
        syncElementTree(updatedElement)
    }

    private fun syncElementTree(updatedElement: LayoutElement) {
        element = updatedElement
        val updatedChildren = updatedElement.childElements()
        require(updatedChildren.size == children.size) {
            "Equivalent layout trees must preserve child counts"
        }
        when (updatedElement) {
            is LayoutElement.ScrollableColumn -> {
                scrollMetrics = scrollMetrics?.let { previous ->
                    resolveScrollMetrics(
                        bounds = bounds,
                        modifier = updatedElement.modifier,
                        contentMainAxisSize = previous.contentMainAxisSize,
                        state = updatedElement.state,
                        axis = StackAxis.VERTICAL
                    )
                }
            }

            is LayoutElement.ScrollableRow -> {
                scrollMetrics = scrollMetrics?.let { previous ->
                    resolveScrollMetrics(
                        bounds = bounds,
                        modifier = updatedElement.modifier,
                        contentMainAxisSize = previous.contentMainAxisSize,
                        state = updatedElement.state,
                        axis = StackAxis.HORIZONTAL
                    )
                }
            }

            else -> Unit
        }
        children.indices.forEach { index ->
            children[index].updateElements(updatedChildren[index])
        }
    }
}

private fun LayoutElement.childElements(): List<LayoutElement> {
    return when (this) {
        is LayoutElement.Box -> children
        is LayoutElement.Column -> children
        is LayoutElement.ScrollableColumn -> children
        is LayoutElement.ScrollableRow -> children
        is LayoutElement.Row -> children
        is LayoutElement.Text,
        is LayoutElement.Button,
        is LayoutElement.Checkbox,
        is LayoutElement.TextField,
        is LayoutElement.Slider,
        is LayoutElement.SelectableList,
        is LayoutElement.Spacer -> emptyList()
    }
}
