package io.github.fopwoc.mods.hotspot

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import cpw.mods.fml.common.event.FMLServerStoppingEvent
import io.github.fopwoc.mods.framework.FrameworkMod
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.hotspot.server.ProfilingService
import org.apache.logging.log4j.LogManager

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;after:Opis;",
    acceptableRemoteVersions = "*",
    guiFactory = GUI_FACTORY_CLASS,
)
object HotspotMod {
  private val logger = LogManager.getLogger(HotspotMod::class.java)

  @SidedProxy(
      clientSide = CLIENT_PROXY_CLASS,
      serverSide = SERVER_PROXY_CLASS,
  )
  lateinit var proxy: ModProxy

  @Mod.EventHandler
  fun onPreInit(event: FMLPreInitializationEvent) {
    logger.info("Starting {} {}", MOD_NAME, MOD_VERSION)
    FrameworkMod.checkDependent(MOD_ID, MOD_VERSION)
    proxy.preInit(event.modConfigurationDirectory)
  }

  @Mod.EventHandler
  fun onInit(event: FMLInitializationEvent) {
    proxy.init()
    logger.info("{} ready", MOD_NAME)
  }

  @Mod.EventHandler
  fun onServerStopping(event: FMLServerStoppingEvent) {
    ProfilingService.shutdown()
  }
}
