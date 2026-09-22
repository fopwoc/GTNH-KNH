package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.framework.ui.compose.input.Key
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBinding
import io.github.fopwoc.mods.framework.ui.compose.input.KeyBindings
import io.github.fopwoc.mods.framework.ui.compose.screen.Screens
import io.github.fopwoc.mods.palimpsest.client.command.PalimpsestCommand
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig

object PalimpsestEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initializeClient() {
        PalimpsestConfig.register()
        MapSessions.register()
        PalimpsestCommand.register()
        lateinit var openMap: KeyBinding
        openMap = KeyBindings.register("Open world map", modId, Key.M) { Screens.open(MapScreen(openMap)) }
    }
}
