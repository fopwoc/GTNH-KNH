package io.github.fopwoc.mods.framework.platform

import java.io.File

/** Loader facts and event wiring; each platform source set registers one in `META-INF/services`. */
interface PlatformBackend {
    val loader: Loader
    val minecraftVersion: String

    /** Physical side: true in the game client, including its integrated server. */
    val isClient: Boolean
    val gameDirectory: File
    val configDirectory: File

    fun isModLoaded(modId: String): Boolean

    /** Starts firing [io.github.fopwoc.mods.framework.event] events; called once. */
    fun installEvents()
}
