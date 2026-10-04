package io.github.fopwoc.palimpsest.db

/** Every block of one chunk at one moment. Immutable and thread-safe. */
interface ChunkVolume {
    val pos: ChunkPos

    /** Lowest block Y, inclusive. */
    val minY: Int

    /** Highest block Y, exclusive. */
    val maxY: Int

    /** The block at local [x], [z] (0..15) and world [y]; air outside [minY] until [maxY]. */
    fun block(x: Int, y: Int, z: Int): BlockId

    val biomes: Biomes
}
