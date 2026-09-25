package io.github.fopwoc.mods.tabtps

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.platform.Platform
import io.github.fopwoc.mods.tabtps.ModMetadata.MOD_ID
import io.github.fopwoc.mods.tabtps.ModMetadata.MOD_NAME
import io.github.fopwoc.mods.tabtps.ModMetadata.MOD_VERSION

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
    acceptableRemoteVersions = "*",
    guiFactory = "io.github.fopwoc.mods.tabtps.config.gui.TabTpsGuiFactory",
)
object TabTpsMod {
    @Mod.EventHandler
    fun onPreInit(@Suppress("UNUSED_PARAMETER") event: FMLPreInitializationEvent) =
        Platform.initialize(TabTpsEntrypoint)
}
