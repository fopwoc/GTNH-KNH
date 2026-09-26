package io.github.fopwoc.mods.gtnhmeasurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.framework.render.WorldOverlays
import io.github.fopwoc.mods.gtnhmeasurement.client.MeasurementKeyBindings
import io.github.fopwoc.mods.gtnhmeasurement.client.command.OpenMeasurementMenuCommand
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementClientController
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutHudOverlay
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernFreecamReach
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementOverlay
import io.github.fopwoc.mods.gtnhmeasurement.config.MeasurementConfig

object MeasurementEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initialize() {
        MeasurementConfig.register()
    }

    override fun initializeClient() {
        MeasurementClientController.install()
        ModernFreecamReach.install()
        OpenMeasurementMenuCommand.register()
        MeasurementKeyBindings.register()
        ClientBackend.current.registerHud(MeasurementShortcutHudOverlay)
        WorldOverlays.register(ModernMeasurementOverlay::draw)
    }
}
