package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import org.lwjgl.input.Keyboard

/** LWJGL 2 key codes of 1.7.10 as platform-neutral keys. */
internal fun lwjglKeyPress(keyCode: Int): KeyPress =
    KeyPress(
        key =
            when (keyCode) {
                Keyboard.KEY_ESCAPE -> Key.Escape
                Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> Key.Enter
                Keyboard.KEY_TAB -> Key.Tab
                Keyboard.KEY_BACK -> Key.Backspace
                Keyboard.KEY_DELETE -> Key.Delete
                Keyboard.KEY_LEFT -> Key.Left
                Keyboard.KEY_RIGHT -> Key.Right
                Keyboard.KEY_UP -> Key.Up
                Keyboard.KEY_DOWN -> Key.Down
                Keyboard.KEY_HOME -> Key.Home
                Keyboard.KEY_END -> Key.End
                Keyboard.KEY_PRIOR -> Key.PageUp
                Keyboard.KEY_NEXT -> Key.PageDown
                Keyboard.KEY_A -> Key.A
                Keyboard.KEY_C -> Key.C
                Keyboard.KEY_V -> Key.V
                Keyboard.KEY_X -> Key.X
                else -> Key.Unknown
            },
        code = keyCode,
        modifiers = LwjglKeyboardEnvironment.modifiers(),
    )

internal fun isLwjglTypedChar(char: Char): Boolean = char >= ' ' && char != '\u007f'
