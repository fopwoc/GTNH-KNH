package io.github.fopwoc.mods.tabtps.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.tabtps.TabTpsMod
import io.github.fopwoc.mods.tabtps.server.ServerTpsService
import io.github.fopwoc.mods.tabtps.server.network.ServerTpsNetwork

open class CommonProxy : ModProxy() {
  override fun init() {
    ServerTpsNetwork.initialize()
    FMLCommonHandler.instance().bus().register(ServerTpsService)
    TabTpsMod.logger.info("Registered shared TPS protocol and server sampling service")
  }
}
