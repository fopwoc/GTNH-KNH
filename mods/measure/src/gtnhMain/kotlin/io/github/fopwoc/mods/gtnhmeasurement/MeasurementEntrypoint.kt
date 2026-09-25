package io.github.fopwoc.mods.gtnhmeasurement

import cpw.mods.fml.common.FMLCommonHandler
import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.command.OpenMeasurementMenuCommand
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementClientController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementOverlayRenderer
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutHudOverlay
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementWorldInteractionController
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig
import net.minecraftforge.common.MinecraftForge

object MeasurementEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {
        MeasurementConfig.register()
    }

    override fun initializeClient() {
        MeasurementClientController.install()
        MinecraftForge.EVENT_BUS.register(MeasurementOverlayRenderer)
        MinecraftForge.EVENT_BUS.register(MeasurementShortcutHudOverlay)
        MinecraftForge.EVENT_BUS.register(MeasurementWorldInteractionController)
        MinecraftForge.EVENT_BUS.register(MeasurementClientController)
        FMLCommonHandler.instance().bus().register(MeasurementClientController)
        OpenMeasurementMenuCommand.register()
        MeasurementKeyBindings.register()
    }
}
