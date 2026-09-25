package io.github.fopwoc.mods.palimpsest.client

import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBindings
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.palimpsest.ModMetadata
import io.github.fopwoc.mods.palimpsest.client.command.PalimpsestCommand
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapPlatform
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig

/** The client side every loader shares; entrypoints only supply the [MapPlatform]. */
object PalimpsestClient {
    fun initialize(platform: MapPlatform) {
        PalimpsestConfig.register()
        MapSessions.register(platform)
        PalimpsestCommand.register()
        lateinit var openMap: KeyBinding
        openMap = KeyBindings.register("key.palimpsest.openMap", ModMetadata.MOD_ID, Key.M) { Screens.open(MapScreen(openMap)) }
    }
}
