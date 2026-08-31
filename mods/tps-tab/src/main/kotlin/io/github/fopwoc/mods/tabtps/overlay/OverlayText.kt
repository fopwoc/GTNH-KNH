package io.github.fopwoc.mods.tabtps.overlay

internal object OverlayText {
  private const val ELLIPSIS = "…"

  fun ellipsize(
      text: String,
      maxWidth: Int,
      widthOf: (String) -> Int,
      trimToWidth: (String, Int) -> String,
  ): String {
    if (maxWidth <= 0 || widthOf(text) <= maxWidth) {
      return if (maxWidth <= 0) "" else text
    }

    val ellipsisWidth = widthOf(ELLIPSIS)
    if (ellipsisWidth >= maxWidth) {
      return trimToWidth(ELLIPSIS, maxWidth)
    }

    return trimToWidth(text, maxWidth - ellipsisWidth).trimEnd() + ELLIPSIS
  }
}
