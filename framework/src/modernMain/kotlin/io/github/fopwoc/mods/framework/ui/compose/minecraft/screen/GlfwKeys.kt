package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

/** GLFW key events of Minecraft 26.x as platform-neutral keys; Command counts as Ctrl on macOS. */
internal fun KeyEvent.toKeyPress(): KeyPress =
    KeyPress(
        key = glfwKey(key()),
        code = key(),
        modifiers = KeyModifiers(ctrl = hasControlDownWithQuirk(), shift = hasShiftDown(), alt = hasAltDown()),
    )

internal fun glfwKey(code: Int): Key = GLFW_KEYS[code] ?: Key.Unknown

internal fun glfwCode(key: Key): Int = GLFW_CODES[key] ?: GLFW.GLFW_KEY_UNKNOWN

private val GLFW_KEYS: Map<Int, Key> =
    mapOf(
        GLFW.GLFW_KEY_ESCAPE to Key.Escape,
        GLFW.GLFW_KEY_ENTER to Key.Enter,
        GLFW.GLFW_KEY_TAB to Key.Tab,
        GLFW.GLFW_KEY_BACKSPACE to Key.Backspace,
        GLFW.GLFW_KEY_DELETE to Key.Delete,
        GLFW.GLFW_KEY_INSERT to Key.Insert,
        GLFW.GLFW_KEY_SPACE to Key.Space,
        GLFW.GLFW_KEY_LEFT to Key.Left,
        GLFW.GLFW_KEY_RIGHT to Key.Right,
        GLFW.GLFW_KEY_UP to Key.Up,
        GLFW.GLFW_KEY_DOWN to Key.Down,
        GLFW.GLFW_KEY_HOME to Key.Home,
        GLFW.GLFW_KEY_END to Key.End,
        GLFW.GLFW_KEY_PAGE_UP to Key.PageUp,
        GLFW.GLFW_KEY_PAGE_DOWN to Key.PageDown,
        GLFW.GLFW_KEY_A to Key.A,
        GLFW.GLFW_KEY_B to Key.B,
        GLFW.GLFW_KEY_C to Key.C,
        GLFW.GLFW_KEY_D to Key.D,
        GLFW.GLFW_KEY_E to Key.E,
        GLFW.GLFW_KEY_F to Key.F,
        GLFW.GLFW_KEY_G to Key.G,
        GLFW.GLFW_KEY_H to Key.H,
        GLFW.GLFW_KEY_I to Key.I,
        GLFW.GLFW_KEY_J to Key.J,
        GLFW.GLFW_KEY_K to Key.K,
        GLFW.GLFW_KEY_L to Key.L,
        GLFW.GLFW_KEY_M to Key.M,
        GLFW.GLFW_KEY_N to Key.N,
        GLFW.GLFW_KEY_O to Key.O,
        GLFW.GLFW_KEY_P to Key.P,
        GLFW.GLFW_KEY_Q to Key.Q,
        GLFW.GLFW_KEY_R to Key.R,
        GLFW.GLFW_KEY_S to Key.S,
        GLFW.GLFW_KEY_T to Key.T,
        GLFW.GLFW_KEY_U to Key.U,
        GLFW.GLFW_KEY_V to Key.V,
        GLFW.GLFW_KEY_W to Key.W,
        GLFW.GLFW_KEY_X to Key.X,
        GLFW.GLFW_KEY_Y to Key.Y,
        GLFW.GLFW_KEY_Z to Key.Z,
        GLFW.GLFW_KEY_0 to Key.Num0,
        GLFW.GLFW_KEY_1 to Key.Num1,
        GLFW.GLFW_KEY_2 to Key.Num2,
        GLFW.GLFW_KEY_3 to Key.Num3,
        GLFW.GLFW_KEY_4 to Key.Num4,
        GLFW.GLFW_KEY_5 to Key.Num5,
        GLFW.GLFW_KEY_6 to Key.Num6,
        GLFW.GLFW_KEY_7 to Key.Num7,
        GLFW.GLFW_KEY_8 to Key.Num8,
        GLFW.GLFW_KEY_9 to Key.Num9,
        GLFW.GLFW_KEY_F1 to Key.F1,
        GLFW.GLFW_KEY_F2 to Key.F2,
        GLFW.GLFW_KEY_F3 to Key.F3,
        GLFW.GLFW_KEY_F4 to Key.F4,
        GLFW.GLFW_KEY_F5 to Key.F5,
        GLFW.GLFW_KEY_F6 to Key.F6,
        GLFW.GLFW_KEY_F7 to Key.F7,
        GLFW.GLFW_KEY_F8 to Key.F8,
        GLFW.GLFW_KEY_F9 to Key.F9,
        GLFW.GLFW_KEY_F10 to Key.F10,
        GLFW.GLFW_KEY_F11 to Key.F11,
        GLFW.GLFW_KEY_F12 to Key.F12,
        GLFW.GLFW_KEY_MINUS to Key.Minus,
        GLFW.GLFW_KEY_EQUAL to Key.Equals,
        GLFW.GLFW_KEY_GRAVE_ACCENT to Key.Grave,
        GLFW.GLFW_KEY_LEFT_SHIFT to Key.LeftShift,
        GLFW.GLFW_KEY_RIGHT_SHIFT to Key.RightShift,
        GLFW.GLFW_KEY_LEFT_CONTROL to Key.LeftCtrl,
        GLFW.GLFW_KEY_RIGHT_CONTROL to Key.RightCtrl,
        GLFW.GLFW_KEY_LEFT_ALT to Key.LeftAlt,
        GLFW.GLFW_KEY_RIGHT_ALT to Key.RightAlt,
        GLFW.GLFW_KEY_LEFT_SUPER to Key.LeftMeta,
        GLFW.GLFW_KEY_RIGHT_SUPER to Key.RightMeta,
        GLFW.GLFW_KEY_KP_ENTER to Key.Enter,
    )

private val GLFW_CODES: Map<Key, Int> = GLFW_KEYS.entries.reversed().associate { (code, key) -> key to code }
