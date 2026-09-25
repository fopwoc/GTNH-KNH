package io.github.fopwoc.mods.hotspot.client

import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBindings
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreen

/** Unbound by default; assign it under Options → Controls → Hotspot. */
object HotspotKeyBindings {
    lateinit var openMenu: KeyBinding
        private set

    fun register() {
        openMenu =
            KeyBindings.register("key.hotspot.openMenu", "hotspot") {
                Screens.open(HotspotScreen())
            }
    }
}
