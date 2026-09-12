package io.github.fopwoc.mods.tabtps.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import io.github.fopwoc.mods.tabtps.overlay.TabTpsOverlay
import java.io.File
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

@Suppress("unused")
class ClientProxy : CommonProxy() {
  private val logger = LogManager.getLogger(ClientProxy::class.java)

  override fun preInit(configDirectory: File) {
    TabTpsConfig.load(configDirectory)
    logger.info("Loaded client TPS overlay configuration")
  }

  override fun init() {
    super.init()
    ClientTpsNetwork.initialize()
    FMLCommonHandler.instance().bus().register(TabTpsMonitor)
    FMLCommonHandler.instance().bus().register(TabTpsConfig)
    MinecraftForge.EVENT_BUS.register(TabTpsOverlay)
    logger.info("Registered client-side TPS requests and tab overlay")
  }
}
