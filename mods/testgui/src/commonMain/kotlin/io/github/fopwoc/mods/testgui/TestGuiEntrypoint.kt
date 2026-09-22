package io.github.fopwoc.mods.testgui

import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.framework.ui.compose.hud.Hud
import io.github.fopwoc.mods.testgui.client.command.TestGuiCommand
import io.github.fopwoc.mods.testgui.client.hud.TestGuiHudOverlay

object TestGuiEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initializeClient() {
        TestGuiCommand.register()
        Hud.register(TestGuiHudOverlay)
    }
}
