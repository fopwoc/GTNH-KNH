package io.github.fopwoc.mods.framework.platform

/**
 * A mod's loader-independent entry. Each loader's thin entrypoint passes it to
 * [Platform.initialize] at the earliest point the loader allows registrations: GTNH pre-init,
 * NeoForge mod construction, Fabric `onInitialize`.
 */
interface ModEntrypoint {
    val modId: String
    val modName: String
    val modVersion: String

    /** Both sides: configs, network channels, server event subscriptions. */
    fun initialize() = Unit

    /** Physical client only, after [initialize]; client-only classes are safe to touch here. */
    fun initializeClient() = Unit
}
