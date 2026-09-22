package io.github.fopwoc.mods.framework.ui.compose.minecraft.screen

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyModifiers
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

/** GLFW key events of Minecraft 26.x as platform-neutral keys; Command counts as Ctrl on macOS. */
internal fun KeyEvent.toKeyPress(): KeyPress =
    KeyPress(
        key =
            when (key()) {
                GLFW.GLFW_KEY_ESCAPE -> Key.Escape
                GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Key.Enter
                GLFW.GLFW_KEY_TAB -> Key.Tab
                GLFW.GLFW_KEY_BACKSPACE -> Key.Backspace
                GLFW.GLFW_KEY_DELETE -> Key.Delete
                GLFW.GLFW_KEY_LEFT -> Key.Left
                GLFW.GLFW_KEY_RIGHT -> Key.Right
                GLFW.GLFW_KEY_UP -> Key.Up
                GLFW.GLFW_KEY_DOWN -> Key.Down
                GLFW.GLFW_KEY_HOME -> Key.Home
                GLFW.GLFW_KEY_END -> Key.End
                GLFW.GLFW_KEY_PAGE_UP -> Key.PageUp
                GLFW.GLFW_KEY_PAGE_DOWN -> Key.PageDown
                GLFW.GLFW_KEY_A -> Key.A
                GLFW.GLFW_KEY_C -> Key.C
                GLFW.GLFW_KEY_V -> Key.V
                GLFW.GLFW_KEY_X -> Key.X
                else -> Key.Unknown
            },
        code = key(),
        modifiers = KeyModifiers(ctrl = hasControlDownWithQuirk(), shift = hasShiftDown(), alt = hasAltDown()),
    )
