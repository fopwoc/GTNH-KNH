package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.client.ClientKeyBindings
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.palimpsest.client.command.PalimpsestCommand
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import net.minecraft.client.settings.KeyBinding
import org.lwjgl.input.Keyboard

object PalimpsestEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initializeClient() {
        PalimpsestConfig.register()
        MapSessions.register()
        PalimpsestCommand.register()
        lateinit var openMap: KeyBinding
        openMap =
            ClientKeyBindings.bind("Open world map", modName, Keyboard.KEY_M) {
                ScreenOpener.open { MapScreen(openMap) }
            }
    }
}
