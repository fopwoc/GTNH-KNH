package io.github.fopwoc.mods.palimpsest

import io.github.fopwoc.mods.framework.platform.Platform
import net.neoforged.fml.common.Mod

@Mod(ModMetadata.MOD_ID)
object PalimpsestNeoForge {
    init {
        Platform.initialize(PalimpsestEntrypoint)
    }
}
