package io.github.fopwoc.mods.gtnhmeasurement

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.common.event.FMLInitializationEvent
import cpw.mods.fml.common.event.FMLPreInitializationEvent
import io.github.fopwoc.mods.framework.ModProxy
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:knhcore;",
)
object MeasurementMod {
  lateinit var logger: Logger

  @SidedProxy(
      clientSide = CLIENT_PROXY_CLASS,
      serverSide = SERVER_PROXY_CLASS,
  )
  lateinit var proxy: ModProxy

  @Mod.EventHandler
  fun onPreInit(event: FMLPreInitializationEvent) {
    logger = LogManager.getLogger(MeasurementMod::class.java)
    logger.info("Starting {} {}", MOD_NAME, MOD_VERSION)
  }

  @Mod.EventHandler
  fun onInit(event: FMLInitializationEvent) {
    proxy.init()
    logger.info("{} ready", MOD_NAME)
  }
}
