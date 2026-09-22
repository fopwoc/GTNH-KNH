package io.github.fopwoc.mods.testgui

import io.github.fopwoc.mods.framework.platform.Platform
import net.neoforged.fml.common.Mod

@Mod(ModMetadata.MOD_ID)
object TestGuiNeoForge {
    init {
        Platform.initialize(TestGuiEntrypoint)
    }
}
