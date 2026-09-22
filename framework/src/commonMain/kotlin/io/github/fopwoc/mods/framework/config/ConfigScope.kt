package io.github.fopwoc.mods.framework.config

/** Where a config lives; backends without the distinction treat every scope alike. */
enum class ConfigScope {
    /** Player preferences, never synced. */
    CLIENT,

    /** Shared by client and server installs, loaded on both. */
    COMMON,

    /** Per world, owned by the server; modern loaders sync it to clients. */
    SERVER,
}
