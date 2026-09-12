package io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile

import io.github.fopwoc.mods.hotspot.client.profile.ChunkRef
import io.github.fopwoc.mods.hotspot.client.profile.TileEntityRef

data class ProfileModel(
    val statusLine: String,
    val canProfile: Boolean,
    val durationSeconds: Int,
    val durationOptions: List<Int>,
    val hasSnapshot: Boolean,
    val emptyHint: String,
    val dimensionLabel: String,
    val canCycleDimensions: Boolean,
    val dimensionSummary: String,
    val chunks: List<ChunkRow>,
    val focusedChunkIndex: Int,
    val tileEntities: List<TileEntityRow>,
    val selectedTileEntityIndices: Set<Int>,
    val selectionSummary: String,
)

data class ChunkRow(val ref: ChunkRef, val label: String)

data class TileEntityRow(val ref: TileEntityRef, val label: String)
