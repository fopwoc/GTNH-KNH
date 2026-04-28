package io.github.fopwoc.mods.framework.ui.compose.layout.text

import io.github.fopwoc.mods.framework.ui.compose.layout.core.Size
import io.github.fopwoc.mods.framework.ui.compose.layout.core.availableInnerWidth
import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.element.LayoutElement
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.model.style.TextStyle
import io.github.fopwoc.mods.framework.ui.compose.text.StyledText
import kotlin.math.max

internal fun measureTextNaturalSize(
    element: LayoutElement.Text,
    metrics: TextMetrics,
    maxWidth: Int
): Size = measureTextNaturalSize(
    modifier = element.modifier,
    text = element.text,
    style = element.style,
    metrics = metrics,
    maxWidth = maxWidth
)

internal fun measureTextNaturalSize(
    modifier: Modifier,
    text: StyledText,
    style: TextStyle,
    metrics: TextMetrics,
    maxWidth: Int
): Size {
    val padding = modifier.padding
    val contentWidthLimit = availableInnerWidth(modifier, maxWidth)
    val formattedText = text.formattedString
    val lines = if (style.wrap && contentWidthLimit > 0) {
        metrics.wrapText(formattedText, contentWidthLimit).ifEmpty { listOf("") }
    } else {
        listOf(formattedText)
    }
    val widestLineWidth = lines.maxOfOrNull(metrics::textWidth) ?: 0
    val textHeight = max(1, lines.size) * metrics.lineHeight + if (style.shadow) 1 else 0
    return Size(
        width = when {
            modifier.fixedWidth != null || modifier.fillMaxWidth -> contentWidthLimit + padding.horizontalValue
            else -> widestLineWidth + padding.horizontalValue
        },
        height = textHeight + padding.verticalValue
    )
}

