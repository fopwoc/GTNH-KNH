package io.github.fopwoc.mods.framework.serialization

import io.github.fopwoc.mods.framework.log.Logger
import io.github.fopwoc.mods.framework.platform.Platform
import java.io.File
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * One JSON file per world/server under `config/<modId>/<directory>/<contextId>.json`, keyed by
 * [ClientBackend.currentWorldId][io.github.fopwoc.mods.framework.client.ClientBackend.currentWorldId].
 * Read failures log and fall back to [defaultValue]; write failures log. See [WorldScopedSync] for
 * the load-on-join / save-when-dirty cycle.
 */
class WorldScopedJsonStore<T : Any>(
    private val modId: String,
    private val directory: String,
    private val serializer: KSerializer<T>,
    private val defaultValue: () -> T,
    private val json: Json = FrameworkJson.prettyConfig,
) {
    private val logger = Logger.named("${WorldScopedJsonStore::class.java.name}.$modId")

    fun file(contextId: String): File =
        JsonFileStorage.modConfigFile(
            Platform.gameDirectory,
            modId,
            directory,
            "$contextId.json",
        )

    fun load(contextId: String): T {
        val target = file(contextId)
        if (!target.isFile) {
            return defaultValue()
        }
        return runCatching { json.decodeFromString(serializer, target.readText()) }
            .onFailure { logger.warn("Failed to read {}", target, it) }
            .getOrElse { defaultValue() }
    }

    fun save(contextId: String, value: T) {
        val target = file(contextId)
        runCatching { JsonFileStorage.writeText(target, json.encodeToString(serializer, value)) }
            .onFailure { logger.warn("Failed to write {}", target, it) }
    }
}
