package io.github.fopwoc.mods.palimpsest

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.platform.Platform
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_ID
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_NAME
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_VERSION

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
    guiFactory = "io.github.fopwoc.mods.palimpsest.config.gui.PalimpsestGuiFactory",
)
object PalimpsestMod {
    @Mod.EventHandler
    fun onPreInit(@Suppress("UNUSED_PARAMETER") event: FMLPreInitializationEvent) = Platform.initialize(PalimpsestEntrypoint)
}
