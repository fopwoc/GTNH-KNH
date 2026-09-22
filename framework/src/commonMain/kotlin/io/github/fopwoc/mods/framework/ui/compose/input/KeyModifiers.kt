package io.github.fopwoc.mods.framework.ui.compose.input

/** Modifier keys held with a key press. On macOS, Command counts as [ctrl]. */
data class KeyModifiers(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
) {
    companion object {
        val None = KeyModifiers()
    }
}
