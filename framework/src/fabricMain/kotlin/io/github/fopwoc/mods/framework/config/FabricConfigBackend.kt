package io.github.fopwoc.mods.framework.config

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry
import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader

/** Fabric has no config system of its own; Forge Config API Port brings NeoForge's to it. */
class FabricConfigBackend : ConfigBackend {
    override fun register(config: ModConfig) {
        val binding = ModConfigSpecBinding(config)
        ModConfigEvents.loading(config.modId).register { loaded ->
            if (loaded.spec === binding.spec) binding.synchronize()
        }
        ModConfigEvents.reloading(config.modId).register { loaded ->
            if (loaded.spec === binding.spec) binding.synchronize()
        }
        ConfigRegistry.INSTANCE.register(config.modId, binding.type, binding.spec, binding.fileName)
        if (FabricLoader.getInstance().environmentType == EnvType.CLIENT)
            FabricConfigScreens.register(config.modId)
    }
}
