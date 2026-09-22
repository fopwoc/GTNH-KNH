package io.github.fopwoc.mods.hotspot.protocol

/**
 * One page of a [ProfileSnapshot] on the wire. A page carries at most one dimension; big dimensions
 * span several pages, and the last page of the whole snapshot sets [isLast].
 */
data class ProfileSnapshotPart(
    val requestId: Long,
    val takenAtEpochMillis: Long,
    val durationTicks: Int,
    val partIndex: Int,
    val isLast: Boolean,
    val dimension: DimensionPage?,
)

data class DimensionPage(
    val id: Int,
    val name: String,
    val tickMs: Double,
    val chunks: List<ChunkProfile>,
)
