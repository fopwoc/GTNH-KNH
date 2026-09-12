package io.github.fopwoc.mods.hotspot.protocol

/** One completed profiling run as the client sees it: every dimension the server ticked. */
data class ProfileSnapshot(
    val requestId: Long,
    val takenAtEpochMillis: Long,
    val durationTicks: Int,
    val dimensions: List<DimensionProfile>,
) {
  fun dimension(id: Int): DimensionProfile? = dimensions.firstOrNull { it.id == id }
}

/**
 * [tickMs] is the mean time of one full tick of that world; the per-chunk numbers only cover tile
 * entities and entities, so the difference is block ticks and other unattributed work.
 */
data class DimensionProfile(
    val id: Int,
    val name: String,
    val tickMs: Double,
    val chunks: List<ChunkProfile>,
) {
  val tileEntityMs: Double
    get() = chunks.sumOf { it.tileEntityMs }

  val entityMs: Double
    get() = chunks.sumOf { it.entityMs }
}

data class ChunkProfile(
    val chunkX: Int,
    val chunkZ: Int,
    val tileEntityMs: Double,
    val entityMs: Double,
    val tileEntityCount: Int,
    val entityCount: Int,
    /** Heaviest first; may be shorter than [tileEntityCount] when the server trimmed the list. */
    val tileEntities: List<TileEntityProfile>,
) {
  val totalMs: Double
    get() = tileEntityMs + entityMs
}

data class TileEntityProfile(
    val x: Int,
    val y: Int,
    val z: Int,
    val ms: Double,
    /** Human-facing name, e.g. the GT machine name or the block's item name. */
    val name: String,
    /** Simple class name of the tile entity, the "technical" name. */
    val className: String,
)
