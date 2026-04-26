package io.github.fopwoc.mods.framework.ui.compose.layout.render

import io.github.fopwoc.mods.framework.ui.compose.layout.core.InputTarget
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

internal interface TextMetrics {
    val lineHeight: Int

    fun textWidth(text: String): Int

    fun wrapText(text: String, maxWidth: Int): List<String>
}

internal interface RenderContext : TextMetrics {
    val viewportWidth: Int
    val viewportHeight: Int
    val mouseX: Int
    val mouseY: Int

    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color)

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color)

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color)

    fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean)

    fun registerInputTarget(target: InputTarget)

    fun withClipRect(rect: Rect, block: () -> Unit)
}
