package io.github.fopwoc.mods.framework.ui.compose.layout.render

internal interface TextMetrics {
  val lineHeight: Int

  fun textWidth(text: String): Int

  fun wrapText(text: String, maxWidth: Int): List<String>

  /** Longest prefix (or suffix when [fromEnd]) of [text] that fits into [maxWidth]. */
  fun trimToWidth(text: String, maxWidth: Int, fromEnd: Boolean = false): String {
    var width = 0
    val builder = StringBuilder()
    val indices = if (fromEnd) text.indices.reversed() else text.indices
    for (index in indices) {
      val char = text[index]
      val charWidth = textWidth(char.toString())
      if (width + charWidth > maxWidth) {
        break
      }
      width += charWidth
      if (fromEnd) builder.insert(0, char) else builder.append(char)
    }
    return builder.toString()
  }
}
