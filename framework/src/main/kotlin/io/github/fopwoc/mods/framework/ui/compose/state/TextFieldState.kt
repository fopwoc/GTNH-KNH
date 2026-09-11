package io.github.fopwoc.mods.framework.ui.compose.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Editable text plus selection and focus, observable by composition. Assigning [text] keeps the
 * selection inside the new bounds; [edit] changes both atomically.
 */
@Stable
class TextFieldState(initialText: String = "") {
  private var textState by mutableStateOf(initialText)
  private var selectionState by mutableStateOf(TextRange(initialText.length))

  var text: String
    get() = textState
    set(value) {
      if (value != textState) {
        textState = value
        selectionState = selectionState.coerceIn(value.length)
      }
    }

  var selection: TextRange
    get() = selectionState
    set(value) {
      selectionState = value.coerceIn(textState.length)
    }

  var focused by mutableStateOf(false)
    private set

  /** First visible character while the field is scrolled horizontally; render-only bookkeeping. */
  internal var scrollOffset: Int = 0

  fun requestFocus() {
    focused = true
  }

  fun clearFocus() {
    focused = false
  }

  fun edit(text: String, selection: TextRange) {
    textState = text
    selectionState = selection.coerceIn(text.length)
  }

  fun selectAll() {
    selectionState = TextRange(0, textState.length)
  }

  fun placeCursorAtEnd() {
    selectionState = TextRange(textState.length)
  }
}
