package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import org.lwjgl.input.Keyboard

/** LWJGL 2 key codes of 1.7.10 as platform-neutral keys. */
internal fun lwjglKeyPress(keyCode: Int): KeyPress =
    KeyPress(lwjglKey(keyCode), keyCode, LwjglKeyboardEnvironment.modifiers())

internal fun lwjglKey(keyCode: Int): Key = LWJGL_KEYS[keyCode] ?: Key.Unknown

internal fun lwjglCode(key: Key): Int = LWJGL_CODES[key] ?: Keyboard.KEY_NONE

internal fun isLwjglTypedChar(char: Char): Boolean = char >= ' ' && char != '\u007f'

private val LWJGL_KEYS: Map<Int, Key> =
    mapOf(
        Keyboard.KEY_ESCAPE to Key.Escape,
        Keyboard.KEY_RETURN to Key.Enter,
        Keyboard.KEY_TAB to Key.Tab,
        Keyboard.KEY_BACK to Key.Backspace,
        Keyboard.KEY_DELETE to Key.Delete,
        Keyboard.KEY_INSERT to Key.Insert,
        Keyboard.KEY_SPACE to Key.Space,
        Keyboard.KEY_LEFT to Key.Left,
        Keyboard.KEY_RIGHT to Key.Right,
        Keyboard.KEY_UP to Key.Up,
        Keyboard.KEY_DOWN to Key.Down,
        Keyboard.KEY_HOME to Key.Home,
        Keyboard.KEY_END to Key.End,
        Keyboard.KEY_PRIOR to Key.PageUp,
        Keyboard.KEY_NEXT to Key.PageDown,
        Keyboard.KEY_A to Key.A,
        Keyboard.KEY_B to Key.B,
        Keyboard.KEY_C to Key.C,
        Keyboard.KEY_D to Key.D,
        Keyboard.KEY_E to Key.E,
        Keyboard.KEY_F to Key.F,
        Keyboard.KEY_G to Key.G,
        Keyboard.KEY_H to Key.H,
        Keyboard.KEY_I to Key.I,
        Keyboard.KEY_J to Key.J,
        Keyboard.KEY_K to Key.K,
        Keyboard.KEY_L to Key.L,
        Keyboard.KEY_M to Key.M,
        Keyboard.KEY_N to Key.N,
        Keyboard.KEY_O to Key.O,
        Keyboard.KEY_P to Key.P,
        Keyboard.KEY_Q to Key.Q,
        Keyboard.KEY_R to Key.R,
        Keyboard.KEY_S to Key.S,
        Keyboard.KEY_T to Key.T,
        Keyboard.KEY_U to Key.U,
        Keyboard.KEY_V to Key.V,
        Keyboard.KEY_W to Key.W,
        Keyboard.KEY_X to Key.X,
        Keyboard.KEY_Y to Key.Y,
        Keyboard.KEY_Z to Key.Z,
        Keyboard.KEY_0 to Key.Num0,
        Keyboard.KEY_1 to Key.Num1,
        Keyboard.KEY_2 to Key.Num2,
        Keyboard.KEY_3 to Key.Num3,
        Keyboard.KEY_4 to Key.Num4,
        Keyboard.KEY_5 to Key.Num5,
        Keyboard.KEY_6 to Key.Num6,
        Keyboard.KEY_7 to Key.Num7,
        Keyboard.KEY_8 to Key.Num8,
        Keyboard.KEY_9 to Key.Num9,
        Keyboard.KEY_F1 to Key.F1,
        Keyboard.KEY_F2 to Key.F2,
        Keyboard.KEY_F3 to Key.F3,
        Keyboard.KEY_F4 to Key.F4,
        Keyboard.KEY_F5 to Key.F5,
        Keyboard.KEY_F6 to Key.F6,
        Keyboard.KEY_F7 to Key.F7,
        Keyboard.KEY_F8 to Key.F8,
        Keyboard.KEY_F9 to Key.F9,
        Keyboard.KEY_F10 to Key.F10,
        Keyboard.KEY_F11 to Key.F11,
        Keyboard.KEY_F12 to Key.F12,
        Keyboard.KEY_MINUS to Key.Minus,
        Keyboard.KEY_EQUALS to Key.Equals,
        Keyboard.KEY_GRAVE to Key.Grave,
        Keyboard.KEY_LSHIFT to Key.LeftShift,
        Keyboard.KEY_RSHIFT to Key.RightShift,
        Keyboard.KEY_LCONTROL to Key.LeftCtrl,
        Keyboard.KEY_RCONTROL to Key.RightCtrl,
        Keyboard.KEY_LMENU to Key.LeftAlt,
        Keyboard.KEY_RMENU to Key.RightAlt,
        Keyboard.KEY_LMETA to Key.LeftMeta,
        Keyboard.KEY_RMETA to Key.RightMeta,
        Keyboard.KEY_NUMPADENTER to Key.Enter,
    )

private val LWJGL_CODES: Map<Key, Int> =
    LWJGL_KEYS.entries.reversed().associate { (code, key) -> key to code }
