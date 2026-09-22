package io.github.fopwoc.mods.tabtps

import io.github.fopwoc.mods.framework.platform.Platform
import net.fabricmc.api.ModInitializer

object TabTpsFabric : ModInitializer {
    override fun onInitialize() = Platform.initialize(TabTpsEntrypoint)
}
