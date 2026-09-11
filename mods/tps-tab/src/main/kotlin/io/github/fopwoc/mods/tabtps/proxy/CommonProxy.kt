package io.github.fopwoc.mods.tabtps.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.tabtps.server.ServerTpsService
import io.github.fopwoc.mods.tabtps.server.network.ServerTpsNetwork
import org.apache.logging.log4j.LogManager

open class CommonProxy : ModProxy() {
  private val logger = LogManager.getLogger(CommonProxy::class.java)

  override fun init() {
    ServerTpsNetwork.initialize()
    FMLCommonHandler.instance().bus().register(ServerTpsService)
    logger.info("Registered shared TPS protocol and server sampling service")
  }
}
