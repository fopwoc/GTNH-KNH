package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.TextMetrics
import net.minecraft.client.gui.FontRenderer

internal class MinecraftFontTextMetrics(
    private val font: FontRenderer
) : TextMetrics {
    override val lineHeight: Int
        get() = font.FONT_HEIGHT

    override fun textWidth(text: String): Int = font.getStringWidth(text)

    override fun wrapText(text: String, maxWidth: Int): List<String> {
        if (maxWidth <= 0) {
            return listOf(text)
        }

        return text
            .split('\n')
            .flatMap { segment ->
                if (segment.isEmpty()) {
                    listOf("")
                } else {
                    @Suppress("UNCHECKED_CAST")
                    (font.listFormattedStringToWidth(segment, maxWidth) as? List<String>)
                        ?.ifEmpty { listOf(segment) }
                        ?: listOf(segment)
                }
            }
    }
}

internal class MinecraftPrimitiveDrawer(
    private val font: FontRenderer,
    private val fillRectBlock: (Int, Int, Int, Int, Int) -> Unit,
    private val drawHorizontalLineBlock: (Int, Int, Int, Int) -> Unit,
    private val drawVerticalLineBlock: (Int, Int, Int, Int) -> Unit
) {
    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        fillRectBlock(left, top, right, bottom, color)
    }

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Int) {
        drawHorizontalLineBlock(startX, endX, y, color)
    }

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Int) {
        drawVerticalLineBlock(x, startY, endY, color)
    }

    fun drawText(text: String, x: Int, y: Int, color: Int, shadow: Boolean) {
        if (shadow) {
            font.drawStringWithShadow(text, x, y, color)
        } else {
            font.drawString(text, x, y, color)
        }
    }
}
