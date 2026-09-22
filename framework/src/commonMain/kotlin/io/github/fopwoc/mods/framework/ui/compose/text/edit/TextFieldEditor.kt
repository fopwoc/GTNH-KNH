package io.github.fopwoc.mods.framework.ui.compose.text.edit

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.state.TextRange

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
 * Kotlin so the behaviour is unit-testable.
 */
internal object TextFieldEditor {
    fun isPrintable(char: Char): Boolean = char >= ' ' && char != '' && char != '§'

    /** Returns true when the key was consumed. */
    fun onKey(
        state: TextFieldState,
        key: Key,
        modifiers: KeyModifiers,
        clipboard: TextClipboard,
        maxLength: Int,
        isAllowed: (Char) -> Boolean = ::isPrintable,
    ): Boolean {
        val text = state.text
        val selection = state.selection
        when {
            modifiers.ctrl && key == Key.A -> state.selectAll()
            modifiers.ctrl && key == Key.C -> clipboard.write(selectedText(text, selection))
            modifiers.ctrl && key == Key.X -> {
                clipboard.write(selectedText(text, selection))
                if (!selection.collapsed) {
                    replaceSelection(state, "", maxLength, isAllowed)
                }
            }
            modifiers.ctrl && key == Key.V -> replaceSelection(state, clipboard.read(), maxLength, isAllowed)
            key == Key.Backspace -> deleteTowards(state, backwards = true, byWord = modifiers.ctrl)
            key == Key.Delete -> deleteTowards(state, backwards = false, byWord = modifiers.ctrl)
            key == Key.Left -> moveCursor(state, step(text, selection.end, -1, modifiers.ctrl), modifiers.shift)
            key == Key.Right -> moveCursor(state, step(text, selection.end, +1, modifiers.ctrl), modifiers.shift)
            key == Key.Home -> moveCursor(state, 0, modifiers.shift)
            key == Key.End -> moveCursor(state, text.length, modifiers.shift)
            else -> return false
        }
        return true
    }

    /** Returns true when the character was inserted. */
    fun onCharTyped(
        state: TextFieldState,
        char: Char,
        maxLength: Int,
        isAllowed: (Char) -> Boolean = ::isPrintable,
    ): Boolean {
        if (!isAllowed(char)) return false
        replaceSelection(state, char.toString(), maxLength, isAllowed)
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
