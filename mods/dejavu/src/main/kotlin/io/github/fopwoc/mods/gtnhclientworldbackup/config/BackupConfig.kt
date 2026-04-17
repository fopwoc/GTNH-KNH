package io.github.fopwoc.mods.gtnhclientworldbackup.config

import io.github.fopwoc.mods.framework.config.JsonConfigLoader
import io.github.fopwoc.mods.framework.config.LiveJsonConfig
import io.github.fopwoc.mods.gtnhclientworldbackup.ClientWorldBackupMod
import io.github.fopwoc.mods.gtnhclientworldbackup.MOD_ID
import java.io.File

private const val DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 15
private const val DEFAULT_FLUSH_EVERY_SAVED_CHUNKS = 8
private const val DEFAULT_SAVE_NAME_PREFIX = "observed-"

object BackupConfig {
    private const val FILE_NAME = "${MOD_ID}.json"

    private var liveConfig: LiveJsonConfig<BackupConfigModel>? = null

    var revision: Long = 0
        private set

    var enabled: Boolean = true
        private set

    var autosaveIntervalSeconds: Int = DEFAULT_AUTOSAVE_INTERVAL_SECONDS
        private set

    var flushEverySavedChunks: Int = DEFAULT_FLUSH_EVERY_SAVED_CHUNKS
        private set

    var maxChunkRadius: Int = 0
        private set

    var saveSingleplayer: Boolean = false
        private set

    var showHud: Boolean = false
        private set

    var saveNamePrefix: String = DEFAULT_SAVE_NAME_PREFIX
        private set

    var showChunkHighlights: Boolean = true
        private set

    var highlightOnlyTargetedChunk: Boolean = false
        private set

    var highlightRenderRadiusChunks: Int = 12
        private set

    var highlightFillAlpha: Float = 0.08f
        private set

    var highlightOutlineAlpha: Float = 0.65f
        private set

    fun load(configDirectory: File) {
        liveConfig = JsonConfigLoader.live(
            configDirectory = configDirectory,
            fileName = FILE_NAME,
            defaultValue = ::BackupConfigModel,
            normalize = { config: BackupConfigModel ->
                config.copy(
                    autosaveIntervalSeconds = config.autosaveIntervalSeconds.coerceIn(5, 300),
                    flushEverySavedChunks = config.flushEverySavedChunks.coerceIn(1, 64),
                    maxChunkRadius = config.maxChunkRadius.coerceIn(0, 32),
                    saveNamePrefix = config.saveNamePrefix.trim().takeIf { it.isNotBlank() } ?: DEFAULT_SAVE_NAME_PREFIX,
                    highlightRenderRadiusChunks = config.highlightRenderRadiusChunks.coerceIn(1, 64),
                    highlightFillAlpha = config.highlightFillAlpha.coerceIn(0.0f, 0.40f),
                    highlightOutlineAlpha = config.highlightOutlineAlpha.coerceIn(0.05f, 1.0f)
                )
            },
            onReadFailure = { file, throwable ->
                ClientWorldBackupMod.logger.warn("Failed to read {}, rewriting default config", file.name, throwable)
            }
        )

        apply(liveConfig!!.load())
    }

    fun refreshIfChanged(): Boolean {
        val config = liveConfig ?: return false
        if (!config.refreshIfChanged()) {
            return false
        }

        apply(config.current())
        return true
    }

    private fun apply(config: BackupConfigModel) {
        enabled = config.enabled
        autosaveIntervalSeconds = config.autosaveIntervalSeconds
        flushEverySavedChunks = config.flushEverySavedChunks
        maxChunkRadius = config.maxChunkRadius
        saveSingleplayer = config.saveSingleplayer
        showHud = config.showHud
        saveNamePrefix = config.saveNamePrefix
        showChunkHighlights = config.showChunkHighlights
        highlightOnlyTargetedChunk = config.highlightOnlyTargetedChunk
        highlightRenderRadiusChunks = config.highlightRenderRadiusChunks
        highlightFillAlpha = config.highlightFillAlpha
        highlightOutlineAlpha = config.highlightOutlineAlpha
        revision += 1
    }
}


