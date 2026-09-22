package io.github.fopwoc.mods.testgui

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.ModProxy
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

private const val CLIENT_PROXY_CLASS = "io.github.fopwoc.mods.testgui.proxy.ClientProxy"
private const val SERVER_PROXY_CLASS = "io.github.fopwoc.mods.testgui.proxy.ServerProxy"

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
)
object TestGuiMod {
    lateinit var logger: Logger

    @SidedProxy(
        clientSide = CLIENT_PROXY_CLASS,
        serverSide = SERVER_PROXY_CLASS,
    )
    lateinit var proxy: ModProxy

    @Mod.EventHandler
    fun onPreInit(@Suppress("UNUSED_PARAMETER") event: FMLPreInitializationEvent) {
        logger = LogManager.getLogger(TestGuiMod::class.java)
        logger.info("Starting {} {}", MOD_NAME, MOD_VERSION)
    }

    @Mod.EventHandler
    fun onInit(@Suppress("UNUSED_PARAMETER") event: FMLInitializationEvent) {
        proxy.init()
        logger.info("{} ready", MOD_NAME)
    }
}
