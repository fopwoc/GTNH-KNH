package io.github.fopwoc.mods.testgui

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.testgui.CLIENT_PROXY_CLASS
import io.github.fopwoc.mods.testgui.MOD_ID
import io.github.fopwoc.mods.testgui.MOD_NAME
import io.github.fopwoc.mods.testgui.MOD_VERSION
import io.github.fopwoc.mods.testgui.SERVER_PROXY_CLASS
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;"
)
object TestGuiMod {
    lateinit var logger: Logger

    @SidedProxy(
        clientSide = CLIENT_PROXY_CLASS,
        serverSide = SERVER_PROXY_CLASS
    )
    lateinit var proxy: ModProxy

    @Mod.EventHandler
    fun onPreInit(event: FMLPreInitializationEvent) {
        logger = LogManager.getLogger(MOD_NAME)
    }

    @Mod.EventHandler
    fun onInit(event: FMLInitializationEvent) {
        proxy.init()
        logger.info("{} initialized", MOD_NAME)
    }
}

