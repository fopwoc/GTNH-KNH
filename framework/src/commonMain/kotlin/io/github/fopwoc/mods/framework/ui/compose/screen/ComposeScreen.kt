package io.github.fopwoc.mods.framework.ui.compose.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.input.KeyPress
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

/**
 * A full-screen composed UI, independent of the platform: open it with [Screens.open] and each
 * platform shows it in its native screen. [width] and [height] are the GUI-scaled size and change
 * with the window, recomposing [Content].
 */
abstract class ComposeScreen {
    @Composable abstract fun Content()

    open val background: ComposeBackgroundStyle = ComposeBackgroundStyle.Color(Color(0xA0101010))

    /** Whether singleplayer pauses while the screen is open. */
    open val pausesGame: Boolean = true

    var width: Int by mutableIntStateOf(0)
        internal set

    var height: Int by mutableIntStateOf(0)
        internal set

    internal var closeRequested = false

    /**
     * Keys that no text field, `BackHandler` or `NavHost` consumed, before the platform's handling
     * (Escape closes the screen). Return true to swallow the key.
     */
    open fun onUnhandledKey(press: KeyPress): Boolean = false

    /** Every client tick while open. */
    open fun onTick() = Unit

    open fun onClosed() = Unit

    /** Closes the screen on the next tick, so a click handler may call it safely. */
    fun close() {
        closeRequested = true
    }
}
