package io.github.fopwoc.mods.gtnhmeasurement.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.command.OpenMeasurementMenuCommand
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementClientController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementOverlayRenderer
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutHudOverlay
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementWorldInteractionController
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import java.io.File
import net.minecraftforge.common.MinecraftForge
import org.apache.logging.log4j.LogManager

@Suppress("unused")
class ClientProxy : ModProxy() {
  private val logger = LogManager.getLogger(ClientProxy::class.java)

  override fun preInit(configDirectory: File) {
    MeasurementConfig.load(configDirectory)
  }

  override fun init() {
    FMLCommonHandler.instance().bus().register(MeasurementConfig)
    MinecraftForge.EVENT_BUS.register(MeasurementOverlayRenderer)
    MinecraftForge.EVENT_BUS.register(MeasurementShortcutHudOverlay)
    MinecraftForge.EVENT_BUS.register(MeasurementWorldInteractionController)
    MinecraftForge.EVENT_BUS.register(MeasurementClientController)
    FMLCommonHandler.instance().bus().register(MeasurementClientController)
    OpenMeasurementMenuCommand.register()
    MeasurementKeyBindings.register()
    logger.info("Registered GTNH measurement tools")
  }
}
