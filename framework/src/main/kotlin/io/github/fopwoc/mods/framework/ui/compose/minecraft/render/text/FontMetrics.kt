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

/**
 * Wrapping goes through `FontRenderer.listFormattedStringToWidth`, which is measured in both layout
 * and draw of every frame; a small LRU keyed by text and width removes the repeated work.
 */
internal class TextWrapCache(private val maxEntries: Int = 256) {
  private val entries =
      object : LinkedHashMap<Pair<String, Int>, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<String, Int>, List<String>>
        ): Boolean = size > maxEntries
      }

  fun getOrPut(text: String, maxWidth: Int, compute: () -> List<String>): List<String> =
      entries.getOrPut(text to maxWidth, compute)
}
