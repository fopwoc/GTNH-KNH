package io.github.fopwoc.mods.framework.ui.compose.input

/** One key press: the platform-neutral [key] plus the platform's own [code] for key bindings. */
data class KeyPress(val key: Key, val code: Int, val modifiers: KeyModifiers = KeyModifiers.None)
