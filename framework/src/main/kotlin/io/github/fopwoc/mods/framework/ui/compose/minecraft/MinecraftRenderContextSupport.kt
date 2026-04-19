package io.github.fopwoc.mods.framework.ui.compose.minecraft

import io.github.fopwoc.mods.framework.ui.compose.layout.TextMetrics
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
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
    private val callbacks: MinecraftPrimitiveRenderCallbacks
) {
    fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Color) {
        callbacks.fillRect(left, top, right, bottom, color.argbInt)
    }

    fun drawHorizontalLine(startX: Int, endX: Int, y: Int, color: Color) {
        callbacks.drawHorizontalLine(startX, endX, y, color.argbInt)
    }

    fun drawVerticalLine(x: Int, startY: Int, endY: Int, color: Color) {
        callbacks.drawVerticalLine(x, startY, endY, color.argbInt)
    }

    fun drawText(text: String, x: Int, y: Int, color: Color, shadow: Boolean) {
        if (shadow) {
            font.drawStringWithShadow(text, x, y, color.argbInt)
        } else {
            font.drawString(text, x, y, color.argbInt)
        }
    }
}
