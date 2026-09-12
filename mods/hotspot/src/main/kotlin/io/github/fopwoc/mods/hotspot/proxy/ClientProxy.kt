package io.github.fopwoc.mods.hotspot.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.hotspot.client.HotspotKeyBindings
import io.github.fopwoc.mods.hotspot.client.command.HotspotCommand
import io.github.fopwoc.mods.hotspot.client.gui.HotspotScreenController
import io.github.fopwoc.mods.hotspot.client.overlay.HotspotOverlayRenderer
import io.github.fopwoc.mods.hotspot.client.overlay.HotspotStatusHud
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.config.HotspotConfig
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import java.io.File
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

@Suppress("unused")
class ClientProxy : CommonProxy() {
  private val logger = LogManager.getLogger(ClientProxy::class.java)

  override fun preInit(configDirectory: File) {
    super.preInit(configDirectory)
    HotspotConfig.load(configDirectory)
  }

  override fun init() {
    super.init()
    HotspotChannel.statuses.handle(ProfileStore::onStatus)
    HotspotChannel.parts.handle(ProfileStore::onSnapshotPart)
    FMLCommonHandler.instance().bus().register(ProfileStore)
    FMLCommonHandler.instance().bus().register(HotspotConfig)
    FMLCommonHandler.instance().bus().register(HotspotScreenController)
    MinecraftForge.EVENT_BUS.register(HotspotOverlayRenderer)
    MinecraftForge.EVENT_BUS.register(HotspotStatusHud)
    ClientCommandHandler.instance.registerCommand(HotspotCommand)
    HotspotKeyBindings.register()
    FMLCommonHandler.instance().bus().register(HotspotKeyBindings)
    logger.info("Registered Hotspot menu, overlay and command")
  }
}
