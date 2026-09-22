package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.layout.render.TextFieldHost
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextClipboard
import io.github.fopwoc.mods.framework.ui.compose.text.edit.TextFieldEditor

/**
 * Keeps at most one text field focused per screen. Fields report themselves while drawing; after a
 * frame the manager adopts a field whose state requested focus programmatically and drops focus
 * from fields that left the composition.
 */
internal class TextFieldFocusManager : TextFieldHost {
    var focused: TextFieldState? = null
        private set

    private val renderedThisFrame = LinkedHashMap<TextFieldState, Int>()

    override fun rendered(state: TextFieldState, maxLength: Int) {
        renderedThisFrame[state] = maxLength
    }

    override fun focus(state: TextFieldState) {
        val previous = focused
        if (previous !== state) {
            previous?.clearFocus()
        }
        state.requestFocus()
        focused = state
    }

    fun clearFocus() {
        focused?.clearFocus()
        focused = null
    }

    fun beginFrame() {
        renderedThisFrame.clear()
    }

    fun endFrame() {
        val current = focused
        if (current != null && (current !in renderedThisFrame || !current.focused)) {
            current.clearFocus()
            focused = null
        }
        renderedThisFrame.keys.firstOrNull { it.focused && it !== focused }?.let(::focus)
    }

    fun handleKey(key: Key, modifiers: KeyModifiers, clipboard: TextClipboard): Boolean {
        val target = focused ?: return false
        return TextFieldEditor.onKey(target, key, modifiers, clipboard, maxLength(target))
    }

    fun handleChar(char: Char): Boolean {
        val target = focused ?: return false
        return TextFieldEditor.onCharTyped(target, char, maxLength(target))
    }

    private fun maxLength(target: TextFieldState): Int = renderedThisFrame[target] ?: Int.MAX_VALUE

    fun reset() {
        focused = null
        renderedThisFrame.clear()
    }
}
