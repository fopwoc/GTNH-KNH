package io.github.fopwoc.mods.gtnhclientworldbackup.config

import kotlinx.serialization.Serializable

@Serializable
data class BackupConfigModel(
    val enabled: Boolean = true,
    val autosaveIntervalSeconds: Int = 15,
    val flushEverySavedChunks: Int = 8,
    val maxChunkRadius: Int = 0,
    val saveSingleplayer: Boolean = false,
    val showHud: Boolean = false,
    val saveNamePrefix: String = "observed-",
    val showChunkHighlights: Boolean = true,
    val highlightOnlyTargetedChunk: Boolean = false,
    val highlightRenderRadiusChunks: Int = 12,
    val highlightFillAlpha: Float = 0.08f,
    val highlightOutlineAlpha: Float = 0.65f
)

