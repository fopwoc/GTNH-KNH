package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import kotlin.math.max

internal fun measureTextNaturalSize(
    element: LayoutElement.Text,
    metrics: TextMetrics,
    maxWidth: Int
): Size {
    val padding = element.modifier.padding
    val contentWidthLimit = availableInnerWidth(element.modifier, maxWidth)
    val formattedText = element.text.formattedString
    val lines = if (element.style.wrap && contentWidthLimit > 0) {
        metrics.wrapText(formattedText, contentWidthLimit).ifEmpty { listOf("") }
    } else {
        listOf(formattedText)
    }
    val widestLineWidth = lines.maxOfOrNull(metrics::textWidth) ?: 0
    return Size(
        width = when {
            element.modifier.fixedWidth != null || element.modifier.fillMaxWidth -> contentWidthLimit + padding.horizontalValue
            else -> widestLineWidth + padding.horizontalValue
        },
        height = max(1, lines.size) * metrics.lineHeight + padding.verticalValue
    )
}

