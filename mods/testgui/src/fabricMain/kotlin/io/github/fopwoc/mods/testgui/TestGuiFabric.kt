package io.github.fopwoc.mods.testgui

import io.github.fopwoc.mods.framework.platform.Platform
import net.fabricmc.api.ModInitializer

object TestGuiFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(TestGuiEntrypoint)
}
