package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment

internal fun alignedOffset(
    alignment: HorizontalAlignment,
    available: Int,
    childSize: Int
): Int {
    return when (alignment) {
        HorizontalAlignment.START -> 0
        HorizontalAlignment.CENTER -> ((available - childSize) / 2).coerceAtLeast(0)
        HorizontalAlignment.END -> (available - childSize).coerceAtLeast(0)
    }
}

internal fun alignedOffset(
    alignment: VerticalAlignment,
    available: Int,
    childSize: Int
): Int {
    return when (alignment) {
        VerticalAlignment.TOP -> 0
        VerticalAlignment.CENTER -> ((available - childSize) / 2).coerceAtLeast(0)
        VerticalAlignment.BOTTOM -> (available - childSize).coerceAtLeast(0)
    }
}

