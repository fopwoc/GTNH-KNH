package io.github.fopwoc.mods.framework.ui.compose.screen

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.event.ClientEvents

/**
 * Opens [ComposeScreen]s on the next client tick, outside whatever input or command handler asked,
 * and only while the player is in a world.
 */
object Screens {
    private var pending: ComposeScreen? = null
    private var installed = false

    fun open(screen: ComposeScreen) {
        if (!installed) {
            installed = true
            ClientEvents.tickEnd.subscribe { openPending() }
        }
        pending = screen
    }

    private fun openPending() {
        val screen = pending ?: return
        pending = null
        val backend = ClientBackend.current
        if (backend.isInWorld) backend.openScreen(screen)
    }
}
