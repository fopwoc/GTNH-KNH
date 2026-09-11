package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Immutable

/** Selection inside a text field; [start] is the anchor, [end] the cursor (may precede start). */
@Immutable
data class TextRange(val start: Int, val end: Int) {
  constructor(cursor: Int) : this(cursor, cursor)

  val min: Int
    get() = minOf(start, end)

  val max: Int
    get() = maxOf(start, end)

  val collapsed: Boolean
    get() = start == end

  val length: Int
    get() = max - min

  fun coerceIn(textLength: Int): TextRange =
      TextRange(start.coerceIn(0, textLength), end.coerceIn(0, textLength))

  companion object {
    val Zero = TextRange(0)
  }
}
