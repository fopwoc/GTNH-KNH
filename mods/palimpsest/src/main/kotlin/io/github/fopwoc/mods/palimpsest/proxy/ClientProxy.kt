package io.github.fopwoc.mods.palimpsest.proxy

import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.framework.client.ClientKeyBindings
import io.github.fopwoc.mods.framework.client.ScreenOpener
import io.github.fopwoc.mods.palimpsest.MOD_NAME
import io.github.fopwoc.mods.palimpsest.client.command.PalimpsestCommand
import io.github.fopwoc.mods.palimpsest.client.gui.MapScreen
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import org.lwjgl.input.Keyboard

@Suppress("unused")
class ClientProxy : ModProxy() {
    override fun init() {
        MapSessions.register()
        PalimpsestCommand.register()
        lateinit var openMap: net.minecraft.client.settings.KeyBinding
        openMap =
            ClientKeyBindings.bind("Open world map", MOD_NAME, Keyboard.KEY_M) {
                ScreenOpener.open { MapScreen(openMap) }
            }
    }
}
