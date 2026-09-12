package io.github.fopwoc.mods.hotspot.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import io.github.fopwoc.mods.hotspot.server.ProfilingService
import io.github.fopwoc.mods.hotspot.server.profiler.OpisAvailability
import java.io.File
import org.apache.logging.log4j.LogManager

open class CommonProxy : ModProxy() {
  private val logger = LogManager.getLogger(CommonProxy::class.java)

  override fun preInit(configDirectory: File) {
    HotspotServerConfig.load(configDirectory)
  }

  override fun init() {
    HotspotChannel.requests.handle { request, player -> ProfilingService.handle(player, request) }
    HotspotChannel.accessChecks.handle { check, player ->
      ProfilingService.answerAccessCheck(player, check)
    }
    FMLCommonHandler.instance().bus().register(ProfilingService)
    FMLCommonHandler.instance().bus().register(HotspotServerConfig)
    if (OpisAvailability.isPresent) {
      logger.info("Opis profiler available; profiling requests will be served")
    } else {
      logger.info("Opis not installed; profiling requests will be refused")
    }
  }
}
