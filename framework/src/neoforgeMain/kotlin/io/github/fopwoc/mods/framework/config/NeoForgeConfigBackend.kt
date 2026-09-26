package io.github.fopwoc.mods.framework.config

import io.github.fopwoc.mods.framework.platform.isPhysicalClient
import net.neoforged.fml.ModList
import net.neoforged.fml.event.config.ModConfigEvent

/** NeoForge's native config system; register during mod construction, as NeoForge requires. */
class NeoForgeConfigBackend : ConfigBackend {
    override fun register(config: ModConfig) {
        val container =
            ModList.get().getModContainerById(config.modId).orElseThrow {
                IllegalStateException("No mod container for ${config.modId}")
            }
        val binding = ModConfigSpecBinding(config)
        container.eventBus?.addListener(ModConfigEvent.Loading::class.java) { event ->
            if (event.config.spec === binding.spec) binding.synchronize()
        }
        container.eventBus?.addListener(ModConfigEvent.Reloading::class.java) { event ->
            if (event.config.spec === binding.spec) binding.synchronize()
        }
        container.registerConfig(binding.type, binding.spec, binding.fileName)
        if (isPhysicalClient) NeoForgeConfigScreens.register(container)
    }
}
