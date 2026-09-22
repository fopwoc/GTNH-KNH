package io.github.fopwoc.mods.framework.config

/** GTNH 1.7.10 stores configs as Forge `.cfg` files in the instance's config directory. */
class ForgeConfigBackend : ConfigBackend {
    override fun register(config: ModConfig) = ForgeConfigFiles.register(config)
}
