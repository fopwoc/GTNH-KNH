package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.ui.page.status

data class BackupStatusModel(
    val statusLine: String,
    val detailLine: String,
    val saveName: String?,
    val sourceName: String?,
    val sourceAddress: String?,
    val currentDimensionId: Int?,
    val totalUniqueChunks: Int,
    val currentDimensionChunkCount: Int,
    val nextAutosaveSeconds: Int,
    val captureNowEnabled: Boolean,
    val highlightsEnabled: Boolean,
    val highlightLegend: List<String>,
    val notes: List<String>,
)
