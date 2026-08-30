package io.github.fopwoc.mods.framework

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import org.apache.logging.log4j.LogManager

@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
    dependencies = "required-after:forgelin;required-after:hodgepodge;",
)
object FrameworkMod {
  private val logger = LogManager.getLogger(FrameworkMod::class.java)

  @Mod.EventHandler
  fun onInit(event: FMLInitializationEvent) {
    logger.info("{} {} ready", MOD_NAME, MOD_VERSION)
  }
}
