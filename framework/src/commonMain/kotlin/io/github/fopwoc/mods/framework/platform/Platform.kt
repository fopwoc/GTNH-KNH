package io.github.fopwoc.mods.framework.platform

import io.github.fopwoc.mods.framework.FrameworkMod
import io.github.fopwoc.mods.framework.log.logger
import java.io.File
import java.util.ServiceLoader

/** The loader the game runs on, for common code. */
object Platform {
    private val logger = logger<Platform>()

    private val backend: PlatformBackend by lazy {
        checkNotNull(ServiceLoader.load(PlatformBackend::class.java, PlatformBackend::class.java.classLoader).firstOrNull()) {
            "No KNH Core platform backend; the framework jar for this loader is missing"
        }.also { it.installEvents() }
    }

    val loader: Loader get() = backend.loader
    val minecraftVersion: String get() = backend.minecraftVersion
    val isClient: Boolean get() = backend.isClient
    val gameDirectory: File get() = backend.gameDirectory
    val configDirectory: File get() = backend.configDirectory

    fun isModLoaded(modId: String): Boolean = backend.isModLoaded(modId)

    /** Runs a mod's [ModEntrypoint]; see there for when each loader calls this. */
    fun initialize(entrypoint: ModEntrypoint) {
        logger.info("Starting {} {} on {} {}", entrypoint.modName, entrypoint.modVersion, loader, minecraftVersion)
        FrameworkMod.checkDependent(entrypoint.modId, entrypoint.modVersion)
        entrypoint.initialize()
        if (isClient) entrypoint.initializeClient()
        logger.info("{} initialized", entrypoint.modName)
    }
}
