package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier

internal fun drawContainer(context: RenderContext, bounds: Rect, modifier: Modifier) {
    modifier.backgroundColor?.let { color ->
        context.fillRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, color)
    }
    drawBorder(context, bounds, modifier.borderColor)
}

internal fun drawBorder(context: RenderContext, bounds: Rect, borderColor: Color?) {
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

