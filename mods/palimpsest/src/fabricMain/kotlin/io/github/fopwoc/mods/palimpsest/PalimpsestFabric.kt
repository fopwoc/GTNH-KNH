package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.platform.Platform
import net.fabricmc.api.ModInitializer

object PalimpsestFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(PalimpsestEntrypoint)
}
