package io.github.fopwoc.mods.palimpsest.tree

/** The live map's storage operations shared by historical and latest-only engines. */
interface TileSource : AutoCloseable {
    val latestEpoch: Long

    fun tile(key: TileKey, epoch: Long): TileRecord?

    fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray

    /** Tile supplying the square's sample, for overlaying pending edits at distant zoom. */
    fun representativeTile(level: Int, x: Int, z: Int): TileKey?

    /** Number of changed tiles written. */
    fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int

    fun sealIfDue(): Boolean

    fun seal()
}
