package io.github.fopwoc.mods.gtnhclientworldbackup.backup.model

enum class ChunkHighlightState {
    SAVED_EARLIER,
    SAVED_THIS_SESSION
}

data class BackupUiState(
    val statusLine: String,
    val detailLine: String,
    val saveName: String?,
    val sourceName: String?,
    val sourceAddress: String?,
    val currentDimensionId: Int?,
    val totalUniqueChunks: Int,
    val currentDimensionChunkCount: Int,
    val nextAutosaveSeconds: Int,
    val highlightsEnabled: Boolean,
    val highlightLegend: List<String>,
    val notes: List<String>
)

