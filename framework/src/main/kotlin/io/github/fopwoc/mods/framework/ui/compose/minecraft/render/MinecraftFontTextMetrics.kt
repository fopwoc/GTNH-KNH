package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextMetrics
import net.minecraft.client.gui.FontRenderer

internal class MinecraftFontTextMetrics(
    private val font: FontRenderer,
    private val wrapCache: TextWrapCache = TextWrapCache(),
) : TextMetrics {
  override val lineHeight: Int
    get() = font.FONT_HEIGHT

  override fun textWidth(text: String): Int = font.getStringWidth(text)

  override fun trimToWidth(text: String, maxWidth: Int, fromEnd: Boolean): String =
      font.trimStringToWidth(text, maxWidth, fromEnd)

  override fun wrapText(text: String, maxWidth: Int): List<String> {
    if (maxWidth <= 0) {
      return listOf(text)
    }

    return wrapCache.getOrPut(text, maxWidth) { wrapUncached(text, maxWidth) }
  }

  private fun wrapUncached(text: String, maxWidth: Int): List<String> {
    return text.split('\n').flatMap { segment ->
      if (segment.isEmpty()) {
        listOf("")
      } else {
        @Suppress("UNCHECKED_CAST")
        (font.listFormattedStringToWidth(segment, maxWidth) as? List<String>)?.ifEmpty {
          listOf(segment)
        } ?: listOf(segment)
      }
    }
  }
}
