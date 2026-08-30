package io.github.fopwoc.mods.gtnhmeasurement.proxy

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.ModProxy
import io.github.fopwoc.mods.gtnhmeasurement.MeasurementMod
import io.github.fopwoc.mods.gtnhmeasurement.client.command.OpenMeasurementMenuCommand
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.MeasurementScreenController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementClientController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementOverlayRenderer
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutHudOverlay
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementWorldInteractionController
import net.minecraftforge.client.ClientCommandHandler
import net.minecraftforge.common.MinecraftForge

@Suppress("unused")
class ClientProxy : ModProxy() {
  override fun init() {
    MinecraftForge.EVENT_BUS.register(MeasurementOverlayRenderer)
    MinecraftForge.EVENT_BUS.register(MeasurementShortcutHudOverlay)
    MinecraftForge.EVENT_BUS.register(MeasurementWorldInteractionController)
    MinecraftForge.EVENT_BUS.register(MeasurementClientController)
    FMLCommonHandler.instance().bus().register(MeasurementScreenController)
    FMLCommonHandler.instance().bus().register(MeasurementClientController)
    ClientCommandHandler.instance.registerCommand(OpenMeasurementMenuCommand)
    MeasurementMod.logger.info("Registered GTNH measurement tools")
  }
}
