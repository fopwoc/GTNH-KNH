package io.github.fopwoc.mods.framework.ui.compose.hud

import io.github.fopwoc.mods.framework.client.ClientBackend

object Hud {
    /** Call from `initializeClient`; layers draw in registration order. */
    fun register(layer: HudLayer) = ClientBackend.current.registerHud(layer)
}
