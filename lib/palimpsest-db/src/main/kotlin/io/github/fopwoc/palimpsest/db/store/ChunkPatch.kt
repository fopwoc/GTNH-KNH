package io.github.fopwoc.palimpsest.db.store

import io.github.fopwoc.palimpsest.db.ChunkPos

/**
 * A chunk's new version: its full [slots] (sections bottom-up, then biomes; null is air) and the
 * [mask] of slots that differ from the previous version. Only masked slots go to disk.
 */
internal class ChunkPatch(
    val pos: ChunkPos,
    val minSection: Int,
    val mask: Long,
    val slots: Array<BlobRef?>,
)
