package io.github.fopwoc.mods.framework.ui.compose.input

/**
 * The keys the UI host reacts to, independent of the platform's key codes (LWJGL 2 on GTNH, GLFW
 * on modern loaders). Typed characters arrive separately as characters, never as keys.
 */
enum class Key {
    Escape,
    Enter,
    Tab,
    Backspace,
    Delete,
    Left,
    Right,
    Up,
    Down,
    Home,
    End,
    PageUp,
    PageDown,
    A,
    C,
    V,
    X,

    /** Any key without a meaning to the UI host; screens may still read its platform code. */
    Unknown,
}
