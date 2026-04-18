package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement

internal fun drawTextElement(
    context: RenderContext,
    bounds: Rect,
    element: LayoutElement.Text
) {
    drawContainer(context, bounds, element.modifier)

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

internal fun resolveWrappedLines(
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

