package io.github.fopwoc.mods.tabtps.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.tabtps.TabTpsMod
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import io.github.fopwoc.mods.tabtps.overlay.TabTpsOverlay
import java.io.File
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : CommonProxy() {
  override fun preInit(configDirectory: File) {
    TabTpsConfig.load(configDirectory)
    TabTpsMod.logger.info("Loaded client TPS overlay configuration")
  }

  override fun init() {
    super.init()
    ClientTpsNetwork.initialize()
    FMLCommonHandler.instance().bus().register(ClientTpsNetwork)
    FMLCommonHandler.instance().bus().register(TabTpsMonitor)
    FMLCommonHandler.instance().bus().register(TabTpsOverlay)
    FMLCommonHandler.instance().bus().register(TabTpsConfig)
    MinecraftForge.EVENT_BUS.register(TabTpsOverlay)
    TabTpsMod.logger.info("Registered client-side TPS requests and tab overlay")
  }
}
