package io.github.fopwoc.mods.gtnhmeasurement

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ModernMeasurementOverlay
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents

object MeasurementFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        LevelExtractionEvents.END_EXTRACTION.register { context ->
            ModernMeasurementOverlay.extract(context.camera().position())
        }
    }
}
