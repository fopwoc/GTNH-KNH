package io.github.fopwoc.mods.framework.ui.compose.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.minecraft.ComposeBackgroundStyle

/**
 * A non-pausing, background-less screen for a mod menu that reads mutable runtime state: it bumps
 * [refreshToken] every tick so a route can re-read that state with `LaunchedEffect`.
 */
abstract class ComposeMenuScreen : ComposeScreen() {
    override val background: ComposeBackgroundStyle = ComposeBackgroundStyle.None
    override val pausesGame: Boolean = false

    /** Increments every tick; key a `LaunchedEffect` on it to poll runtime state. */
    var refreshToken: Int by mutableIntStateOf(0)
        private set

    /** Forces a re-read before the next tick, e.g. after a key shortcut changed state. */
    fun refreshNow() {
        refreshToken += 1
    }

    override fun onTick() {
        refreshToken += 1
    }
}
