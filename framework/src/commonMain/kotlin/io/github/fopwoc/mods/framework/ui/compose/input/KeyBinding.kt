package io.github.fopwoc.mods.framework.ui.compose.input

import io.github.fopwoc.mods.framework.client.ClientBackend

/**
 * A key binding players can rebind in the controls screen. [name] and [category] are translation
 * keys (or plain text); [onPress] runs on the client thread, outside screens, once per press.
 */
class KeyBinding internal constructor(
    val name: String,
    val category: String,
    val defaultKey: Key?,
    internal val onPress: () -> Unit,
) {
    /** Whether the bound key is held right now. */
    val isDown: Boolean get() = ClientBackend.current.isBindingDown(this)

    /** Whether [press] hits the bound key, e.g. to close a screen with the key that opened it. */
    fun matches(press: KeyPress): Boolean = ClientBackend.current.bindingMatches(this, press)
}

object KeyBindings {
    /** Call from `initializeClient`; returns the binding for screens and polling. */
    fun register(name: String, category: String, defaultKey: Key? = null, onPress: () -> Unit = {}): KeyBinding =
        KeyBinding(name, category, defaultKey, onPress).also(ClientBackend.current::registerKeyBinding)

    /** Whether [key] is physically held, regardless of bindings. */
    fun isDown(key: Key): Boolean = ClientBackend.current.isKeyDown(key)
}
