package io.github.fopwoc.mods.framework

import net.neoforged.fml.common.Mod

@Mod(ModMetadata.MOD_ID)
object FrameworkNeoForge {
    init {
        FrameworkMod.onInit()
    }
}
