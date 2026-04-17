package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import kotlin.math.max
import kotlin.math.min

class LayoutNode internal constructor(
    val element: LayoutElement,
    val bounds: Rect,
    val children: List<LayoutNode>,
    private val scrollMetrics: ScrollMetrics? = null
) {
    fun draw(context: RenderContext) {
        if (element is LayoutElement.ScrollableColumn) {
            drawContainer(context, element.modifier)
            val metrics = scrollMetrics ?: return
            registerScrollWheelTarget(context, metrics)
            context.withClipRect(metrics.viewport) {
                children.forEach { child ->
                    child.draw(context)
                }
            }
            drawScrollIndicator(context, metrics)
            registerScrollThumbTarget(context, metrics)
            return
        }

        drawNode(context)
        children.forEach { child ->
            child.draw(context)
        }
    }

    private fun drawNode(context: RenderContext) {
        when (val current = element) {
            is LayoutElement.Box,
            is LayoutElement.Column,
            is LayoutElement.Row,
            is LayoutElement.Spacer -> drawContainer(context, current.modifier)
            is LayoutElement.ScrollableColumn -> drawContainer(context, current.modifier)
            is LayoutElement.Text -> drawText(context, current)
            is LayoutElement.Button -> drawButton(context, current)
            is LayoutElement.Checkbox -> drawCheckbox(context, current)
            is LayoutElement.TextField -> drawTextField(context, current)
            is LayoutElement.Slider -> drawSlider(context, current)
            is LayoutElement.SelectableList -> drawSelectableList(context, current)
        }
    }

    private fun drawContainer(context: RenderContext, modifier: Modifier) {
        modifier.backgroundColor?.let { color ->
            context.fillRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, color)
        }
        drawBorder(context, modifier.borderColor)
    }

    private fun drawText(context: RenderContext, element: LayoutElement.Text) {
        drawContainer(context, element.modifier)

        val content = bounds.inset(element.modifier.padding)
        if (content.width <= 0 || content.height <= 0) {
            return
        }

        val lines = wrappedLines(context, element.text, element.style.wrap, content.width)
        val startY = if (lines.size == 1) {
            content.y + ((content.height - context.lineHeight) / 2).coerceAtLeast(0)
        } else {
            content.y
        }

        lines.forEachIndexed { index, line ->
            val drawY = startY + index * context.lineHeight
            if (drawY >= content.y + content.height) {
                return@forEachIndexed
            }

            val lineWidth = context.textWidth(line)
            val drawX = when (element.style.alignment) {
                HorizontalAlignment.START -> content.x
                HorizontalAlignment.CENTER -> content.x + ((content.width - lineWidth) / 2).coerceAtLeast(0)
                HorizontalAlignment.END -> content.x + (content.width - lineWidth).coerceAtLeast(0)
            }
            context.drawText(line, drawX, drawY, element.style.color, element.style.shadow)
        }
    }

    private fun drawButton(context: RenderContext, element: LayoutElement.Button) {
        context.drawVanillaButton(
            bounds = bounds,
            hostKey = element.hostKey,
            text = element.text,
            enabled = element.enabled,
            onClick = element.onClick
        )
    }

    private fun drawCheckbox(context: RenderContext, element: LayoutElement.Checkbox) {
        context.drawVanillaCheckbox(
            bounds = bounds,
            hostKey = element.hostKey,
            label = element.label,
            checked = element.checked,
            enabled = element.enabled,
            onCheckedChange = element.onCheckedChange
        )
    }

    private fun drawTextField(context: RenderContext, element: LayoutElement.TextField) {
        context.drawVanillaTextField(
            bounds = bounds,
            state = element.state,
            placeholder = element.placeholder,
            enabled = element.enabled,
            style = element.style
        )
    }

    private fun drawSlider(context: RenderContext, element: LayoutElement.Slider) {
        context.drawVanillaSlider(
            bounds = bounds,
            hostKey = element.hostKey,
            value = element.value,
            valueRangeStart = element.valueRangeStart,
            valueRangeEnd = element.valueRangeEnd,
            label = element.label,
            suffix = element.suffix,
            enabled = element.enabled,
            showDecimal = element.showDecimal,
            onValueChange = element.onValueChange
        )
    }

    private fun drawSelectableList(context: RenderContext, element: LayoutElement.SelectableList) {
        drawContainer(context, element.modifier)
        context.withClipRect(bounds) {
            context.drawVanillaSelectableList(
                bounds = bounds,
                hostKey = element.hostKey,
                items = element.items,
                selectedIndex = element.selectedIndex,
                rowHeight = element.rowHeight,
                onSelectedIndexChange = element.onSelectedIndexChange
            )
        }
    }

    private fun registerScrollWheelTarget(context: RenderContext, metrics: ScrollMetrics) {
        if (metrics.maxValue <= 0) {
            return
        }

        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.SCROLL_WHEEL,
                bounds = metrics.viewport,
                onWheel = { _, _, wheelDelta ->
                    metrics.state.scrollBy(wheelDeltaToPixels(wheelDelta))
                }
            )
        )
    }

    private fun registerScrollThumbTarget(context: RenderContext, metrics: ScrollMetrics) {
        val thumbRect = scrollThumbRect(metrics) ?: return
        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.SCROLL_THUMB,
                bounds = thumbRect,
                onPress = { _, pressY, button ->
                    if (button != 0) {
                        InputPressResult.Ignored
                    } else {
                        val session = ScrollDragSession(
                            state = metrics.state,
                            trackTop = metrics.viewport.y,
                            trackHeight = metrics.viewport.height,
                            thumbHeight = thumbRect.height,
                            maxValue = metrics.maxValue,
                            grabOffsetY = pressY - thumbRect.y
                        )
                        InputPressResult.captured(
                            ActivePointerSession(
                                button = button,
                                onDragHandler = { _, dragY -> session.dragTo(dragY) }
                            )
                        )
                    }
                }
            )
        )
    }

    private fun drawScrollIndicator(context: RenderContext, metrics: ScrollMetrics) {
        val thumbRect = scrollThumbRect(metrics) ?: return
        if (metrics.viewport.width <= 0 || metrics.viewport.height <= 0) {
            return
        }

        val trackWidth = 4
        val trackLeft = bounds.x + bounds.width - trackWidth - 2
        val trackRight = trackLeft + trackWidth
        val trackTop = metrics.viewport.y
        val trackBottom = metrics.viewport.y + metrics.viewport.height
        context.fillRect(trackLeft, trackTop, trackRight, trackBottom, 0x5535353F)
        context.fillRect(thumbRect.x, thumbRect.y, thumbRect.x + thumbRect.width, thumbRect.y + thumbRect.height, 0xCCB8B8C4.toInt())
    }

    private fun drawBorder(context: RenderContext, borderColor: Int?) {
        val color = borderColor ?: return
        if (bounds.width <= 0 || bounds.height <= 0) {
            return
        }

        val left = bounds.x
        val right = bounds.x + bounds.width - 1
        val top = bounds.y
        val bottom = bounds.y + bounds.height - 1
        context.drawHorizontalLine(left, right, top, color)
        context.drawHorizontalLine(left, right, bottom, color)
        context.drawVerticalLine(left, top, bottom, color)
        context.drawVerticalLine(right, top, bottom, color)
    }

    private fun wrappedLines(
        context: TextMetrics,
        text: String,
        wrap: Boolean,
        maxWidth: Int
    ): List<String> {
        if (!wrap || maxWidth <= 0) {
            return listOf(text)
        }

        return context.wrapText(text, maxWidth).ifEmpty {
            listOf("")
        }
    }

    private fun wheelDeltaToPixels(wheelDelta: Int): Int {
        val steps = when {
            wheelDelta > 0 -> max(1, wheelDelta / 120)
            wheelDelta < 0 -> min(-1, wheelDelta / 120)
            else -> 0
        }
        return -steps * 24
    }

    private fun scrollThumbRect(metrics: ScrollMetrics): Rect? {
        if (metrics.maxValue <= 0 || metrics.viewport.width <= 0 || metrics.viewport.height <= 0) {
            return null
        }

        val trackWidth = 4
        val trackLeft = bounds.x + bounds.width - trackWidth - 2
        val thumbHeight = max(16, metrics.viewport.height * metrics.viewport.height / metrics.contentHeight.coerceAtLeast(1))
            .coerceAtMost(metrics.viewport.height)
        val thumbTravel = (metrics.viewport.height - thumbHeight).coerceAtLeast(0)
        val thumbTop = metrics.viewport.y + if (metrics.maxValue == 0) {
            0
        } else {
            thumbTravel * metrics.state.value / metrics.maxValue
        }

        return Rect(
            x = trackLeft,
            y = thumbTop,
            width = trackWidth,
            height = thumbHeight
        )
    }
}


