package io.github.fopwoc.mods.framework.ui.compose.text.edit

import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.state.TextRange

internal data class KeyModifiers(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
) {
  companion object {
    val None = KeyModifiers()
  }
}

internal interface TextClipboard {
  fun read(): String

  fun write(text: String)

  object None : TextClipboard {
    override fun read(): String = ""

    override fun write(text: String) = Unit
  }
}

/**
 * Keyboard editing rules for [TextFieldState], mirroring vanilla text boxes: typed characters
 * replace the selection, arrows move or extend it (Ctrl jumps words), Home/End, Ctrl+A/C/X/V. Pure
 * Kotlin so the behaviour is unit-testable; LWJGL key codes are used as plain constants.
 */
internal object TextFieldEditor {
  const val KEY_BACK = 14
  const val KEY_A = 30
  const val KEY_X = 45
  const val KEY_C = 46
  const val KEY_V = 47
  const val KEY_HOME = 199
  const val KEY_LEFT = 203
  const val KEY_RIGHT = 205
  const val KEY_END = 207
  const val KEY_DELETE = 211

  fun isPrintable(char: Char): Boolean = char >= ' ' && char != '' && char != '§'

  /** Returns true when the key was consumed. */
  fun onKey(
      state: TextFieldState,
      typedChar: Char,
      keyCode: Int,
      modifiers: KeyModifiers,
      clipboard: TextClipboard,
      maxLength: Int,
      isAllowed: (Char) -> Boolean = ::isPrintable,
  ): Boolean {
    val text = state.text
    val selection = state.selection
    when {
      modifiers.ctrl && keyCode == KEY_A -> state.selectAll()
      modifiers.ctrl && keyCode == KEY_C -> clipboard.write(selectedText(text, selection))
      modifiers.ctrl && keyCode == KEY_X -> {
        clipboard.write(selectedText(text, selection))
        if (!selection.collapsed) {
          replaceSelection(state, "", maxLength, isAllowed)
        }
      }
      modifiers.ctrl && keyCode == KEY_V ->
          replaceSelection(state, clipboard.read(), maxLength, isAllowed)
      keyCode == KEY_BACK -> deleteTowards(state, backwards = true, byWord = modifiers.ctrl)
      keyCode == KEY_DELETE -> deleteTowards(state, backwards = false, byWord = modifiers.ctrl)
      keyCode == KEY_LEFT ->
          moveCursor(state, step(text, selection.end, -1, modifiers.ctrl), modifiers.shift)
      keyCode == KEY_RIGHT ->
          moveCursor(state, step(text, selection.end, +1, modifiers.ctrl), modifiers.shift)
      keyCode == KEY_HOME -> moveCursor(state, 0, modifiers.shift)
      keyCode == KEY_END -> moveCursor(state, text.length, modifiers.shift)
      isAllowed(typedChar) && !modifiers.ctrl ->
          replaceSelection(state, typedChar.toString(), maxLength, isAllowed)
      else -> return false
    }
    return true
  }

  fun replaceSelection(
      state: TextFieldState,
      insertion: String,
      maxLength: Int,
      isAllowed: (Char) -> Boolean = ::isPrintable,
  ) {
    val text = state.text
    val selection = state.selection
    val filtered = insertion.filter(isAllowed)
    val room = (maxLength - (text.length - selection.length)).coerceAtLeast(0)
    val inserted = filtered.take(room)
    val updated = text.substring(0, selection.min) + inserted + text.substring(selection.max)
    state.edit(updated, TextRange(selection.min + inserted.length))
  }

  private fun deleteTowards(state: TextFieldState, backwards: Boolean, byWord: Boolean) {
    val text = state.text
    val selection = state.selection
    if (!selection.collapsed) {
      replaceSelection(state, "", Int.MAX_VALUE)
      return
    }
    val target = step(text, selection.end, if (backwards) -1 else +1, byWord)
    if (target == selection.end) {
      return
    }
    val range = TextRange(selection.end, target)
    state.edit(text.substring(0, range.min) + text.substring(range.max), TextRange(range.min))
  }

  private fun moveCursor(state: TextFieldState, target: Int, extend: Boolean) {
    val selection = state.selection
    state.selection = if (extend) TextRange(selection.start, target) else TextRange(target)
  }

  private fun selectedText(text: String, selection: TextRange): String =
      text.substring(selection.min, selection.max)

  /** One character, or one word boundary when [byWord], from [from] in [direction]. */
  private fun step(text: String, from: Int, direction: Int, byWord: Boolean): Int {
    if (!byWord) {
      return (from + direction).coerceIn(0, text.length)
    }
    var index = from
    if (direction < 0) {
      while (index > 0 && text[index - 1] == ' ') index--
      while (index > 0 && text[index - 1] != ' ') index--
    } else {
      while (index < text.length && text[index] != ' ') index++
      while (index < text.length && text[index] == ' ') index++
    }
    return index
  }
}
