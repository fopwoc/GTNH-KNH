package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.platform.ModEntrypoint
import io.github.fopwoc.mods.palimpsest.client.PalimpsestClient
import io.github.fopwoc.mods.palimpsest.client.map.ModernMapPlatform

object PalimpsestEntrypoint : ModEntrypoint {
    override val modId = ModMetadata.MOD_ID
    override val modName = ModMetadata.MOD_NAME
    override val modVersion = ModMetadata.MOD_VERSION

    override fun initializeClient() = PalimpsestClient.initialize(ModernMapPlatform)
}
