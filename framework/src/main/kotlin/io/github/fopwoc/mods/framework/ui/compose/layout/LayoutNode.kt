package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.resolved
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
            drawWithinClip(context, metrics.viewportBounds) {
                drawChildren(context)
            }
            drawScrollIndicator(context, metrics)
            registerScrollThumbTarget(context, metrics)
            return
        }

        drawNode(context)
        drawChildren(context)
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

        val lines = resolveWrappedLines(context, element.text, element.style.wrap, content.width)
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
            hostKey = element.hostKey,
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
        drawWithinClip(context, bounds) {
            context.drawVanillaSelectableList(
                bounds = bounds,
                hostKey = element.hostKey,
                items = element.items,
                selectedIndex = element.selectedIndex,
                rowHeight = element.rowHeight.resolved,
                onSelectedIndexChange = element.onSelectedIndexChange
            )
        }
    }

    private fun drawChildren(context: RenderContext) {
        children.forEach { child ->
            child.draw(context)
        }
    }

    private fun drawWithinClip(context: RenderContext, clipRect: Rect, block: () -> Unit) {
        context.withClipRect(clipRect, block)
    }

    private fun registerScrollWheelTarget(context: RenderContext, metrics: ScrollMetrics) {
        if (metrics.maxValue <= 0) {
            return
        }

        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.SCROLL_WHEEL,
                bounds = metrics.scrollArea,
                onWheel = { _, _, wheelDelta ->
                    metrics.state.scrollBy(wheelDeltaToPixels(wheelDelta))
                }
            )
        )
    }

    private fun registerScrollThumbTarget(context: RenderContext, metrics: ScrollMetrics) {
        val trackBounds = metrics.trackBounds ?: return
        val thumbBounds = resolveScrollThumbBounds(metrics) ?: return
        context.registerInputTarget(
            InputTarget(
                kind = InputTargetKind.SCROLL_THUMB,
                bounds = thumbBounds,
                onPress = { _, pressY, button ->
                    if (button != 0) {
                        InputPressResult.Ignored
                    } else {
                        val session = ScrollDragSession(
                            state = metrics.state,
                            trackTop = trackBounds.y,
                            trackHeight = trackBounds.height,
                            thumbHeight = thumbBounds.height,
                            maxValue = metrics.maxValue,
                            grabOffsetY = pressY - thumbBounds.y
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
        val trackBounds = metrics.trackBounds ?: return
        val thumbBounds = resolveScrollThumbBounds(metrics) ?: return
        if (metrics.viewportBounds.width <= 0 || metrics.viewportBounds.height <= 0) {
            return
        }

        context.fillRect(trackBounds.x, trackBounds.y, trackBounds.x + trackBounds.width, trackBounds.y + trackBounds.height, 0x5535353F)
        context.fillRect(thumbBounds.x, thumbBounds.y, thumbBounds.x + thumbBounds.width, thumbBounds.y + thumbBounds.height, 0xCCB8B8C4.toInt())
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

    private fun resolveWrappedLines(
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

    private fun resolveScrollThumbBounds(metrics: ScrollMetrics): Rect? {
        val trackBounds = metrics.trackBounds ?: return null
        if (metrics.maxValue <= 0 || metrics.viewportBounds.width <= 0 || metrics.viewportBounds.height <= 0) {
            return null
        }

        val thumbHeight = max(16, metrics.viewportBounds.height * metrics.viewportBounds.height / metrics.contentHeight.coerceAtLeast(1))
            .coerceAtMost(metrics.viewportBounds.height)
        val thumbTravel = (trackBounds.height - thumbHeight).coerceAtLeast(0)
        val thumbTop = trackBounds.y + if (metrics.maxValue == 0) {
            0
        } else {
            thumbTravel * metrics.state.value / metrics.maxValue
        }

        return Rect(
            x = trackBounds.x,
            y = thumbTop,
            width = trackBounds.width,
            height = thumbHeight
        )
    }
}


