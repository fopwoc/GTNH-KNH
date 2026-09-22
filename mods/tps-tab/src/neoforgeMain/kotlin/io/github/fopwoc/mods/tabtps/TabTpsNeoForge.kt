package io.github.fopwoc.mods.tabtps

import io.github.fopwoc.mods.framework.platform.Platform
import net.neoforged.fml.common.Mod

@Mod(ModMetadata.MOD_ID)
object TabTpsNeoForge {
    init {
        Platform.initialize(TabTpsEntrypoint)
    }
}
