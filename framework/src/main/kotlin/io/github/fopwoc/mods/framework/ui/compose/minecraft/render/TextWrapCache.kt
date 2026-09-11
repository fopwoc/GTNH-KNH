package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

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
