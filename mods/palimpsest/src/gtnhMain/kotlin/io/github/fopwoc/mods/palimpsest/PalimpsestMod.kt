package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_ID
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_NAME
import io.github.fopwoc.mods.palimpsest.ModMetadata.MOD_VERSION
import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.ModProxy

private const val CLIENT_PROXY_CLASS = "io.github.fopwoc.mods.palimpsest.proxy.ClientProxy"
private const val SERVER_PROXY_CLASS = "io.github.fopwoc.mods.palimpsest.proxy.ServerProxy"
private const val GUI_FACTORY_CLASS =
    "io.github.fopwoc.mods.palimpsest.config.gui.PalimpsestGuiFactory"

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
    guiFactory = GUI_FACTORY_CLASS,
)
object PalimpsestMod {
    private val logger = logger<PalimpsestMod>()

    @SidedProxy(clientSide = CLIENT_PROXY_CLASS, serverSide = SERVER_PROXY_CLASS)
    lateinit var proxy: ModProxy

    @Mod.EventHandler
    fun onPreInit(event: FMLPreInitializationEvent) {
        logger.info("Starting {} {}", MOD_NAME, MOD_VERSION)
        proxy.preInit(event.modConfigurationDirectory)
    }

    @Mod.EventHandler
    fun onInit(@Suppress("UNUSED_PARAMETER") event: FMLInitializationEvent) {
        proxy.init()
        logger.info("{} ready", MOD_NAME)
    }
}
