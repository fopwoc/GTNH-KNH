package io.github.fopwoc.mods.gtnhmeasurement

import io.github.fopwoc.mods.framework.platform.Platform
import net.neoforged.fml.common.Mod

@Mod(ModMetadata.MOD_ID)
object MeasurementNeoForge {
    init {
        Platform.initialize(MeasurementEntrypoint)
        if (Platform.isClient) MeasurementNeoForgeClient.install()
    }
}
