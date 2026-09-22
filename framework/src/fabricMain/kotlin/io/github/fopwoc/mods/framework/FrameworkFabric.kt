package io.github.fopwoc.mods.framework

import net.fabricmc.api.ModInitializer

object FrameworkFabric : ModInitializer {
    override fun onInitialize() = FrameworkMod.onInit()
}
