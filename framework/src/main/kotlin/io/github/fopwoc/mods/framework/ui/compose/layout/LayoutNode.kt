package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

class LayoutNode internal constructor(
    val element: LayoutElement,
    val bounds: Rect,
    val children: List<LayoutNode>,
    private val scrollMetrics: ScrollMetrics? = null
) {
    fun draw(context: RenderContext) {
        registerModifierTooltip(context)
        when (val current = element) {
            is LayoutElement.ScrollableColumn -> drawScrollableColumnElement(
                context = context,
                bounds = bounds,
                element = current,
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
            is LayoutElement.ScrollableColumn -> Unit
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
        val tooltipLines = element.modifier.tooltipLines ?: return
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
}
