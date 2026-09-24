package io.github.fopwoc.mods.gtnhmeasurement

import io.github.fopwoc.mods.framework.platform.Platform
import net.fabricmc.api.ModInitializer

object MeasurementFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(MeasurementEntrypoint)
}
