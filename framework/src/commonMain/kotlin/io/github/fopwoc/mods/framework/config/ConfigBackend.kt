package io.github.fopwoc.mods.framework.config

import java.util.ServiceLoader

/**
 * The platform's config storage and settings screens. Each platform source set registers its
 * implementation as a `META-INF/services` entry; without one (unit tests, headless tools) configs
 * keep their normalized defaults.
 */
interface ConfigBackend {
    fun register(config: ModConfig)

    companion object {
        val current: ConfigBackend by lazy {
            ServiceLoader.load(ConfigBackend::class.java, ConfigBackend::class.java.classLoader).firstOrNull()
                ?: DefaultsConfigBackend
        }
    }
}

private object DefaultsConfigBackend : ConfigBackend {
    override fun register(config: ModConfig) = config.completeLoad()
}
