package io.github.fopwoc.mods.framework.ui.compose.layout

import io.github.fopwoc.mods.framework.ui.compose.model.modifier.PaddingValues
import kotlin.math.max
import kotlin.math.min

data class Size(
    val width: Int,
    val height: Int
)

data class Rect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    fun contains(pointX: Int, pointY: Int): Boolean {
        return pointX >= x && pointY >= y && pointX < x + width && pointY < y + height
    }

    fun inset(padding: PaddingValues): Rect {
        return Rect(
            x = x + padding.left,
            y = y + padding.top,
            width = (width - padding.horizontal).coerceAtLeast(0),
            height = (height - padding.vertical).coerceAtLeast(0)
        )
    }

    fun intersect(other: Rect): Rect {
        val left = max(x, other.x)
        val top = max(y, other.y)
        val right = min(x + width, other.x + other.width)
        val bottom = min(y + height, other.y + other.height)
        return Rect(
            x = left,
            y = top,
            width = (right - left).coerceAtLeast(0),
            height = (bottom - top).coerceAtLeast(0)
        )
    }
}


