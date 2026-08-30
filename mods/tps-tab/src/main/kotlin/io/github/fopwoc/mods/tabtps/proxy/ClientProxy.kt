package io.github.fopwoc.mods.tabtps.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.tabtps.TabTpsMod
import io.github.fopwoc.mods.tabtps.monitor.TabTpsMonitor
import io.github.fopwoc.mods.tabtps.overlay.TabTpsOverlay
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : ModProxy() {
  override fun init() {
    MinecraftForge.EVENT_BUS.register(TabTpsMonitor)
    FMLCommonHandler.instance().bus().register(TabTpsMonitor)
    MinecraftForge.EVENT_BUS.register(TabTpsOverlay)
    TabTpsMod.logger.info("Registered client-side tab TPS monitor and overlay")
  }
}
