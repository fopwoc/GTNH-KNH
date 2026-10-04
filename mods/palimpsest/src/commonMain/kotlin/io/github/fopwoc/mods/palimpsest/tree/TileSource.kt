package io.github.fopwoc.mods.palimpsest.tree

/** The live map's storage operations shared by historical and latest-only engines. */
interface TileSource : AutoCloseable {
    val latestEpoch: Long

    fun tile(key: TileKey, epoch: Long): TileRecord?

    /** Resolve a viewport in one operation; historical trees share traversal between tiles. */
    fun tiles(
        x0: Int,
        z0: Int,
        side: Int,
        epoch: Long,
        checkActive: () -> Unit = {},
    ): Array<TileRecord?> {
        require(side in 1..129)
        return Array(side * side) { offset ->
            checkActive()
            tile(TileKey(x0 + offset % side, z0 + offset / side), epoch)
        }
    }

    fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray

    /**
     * Tiles whose look differs between epochs [from] ≤ [to], or null when this source cannot tell.
     */
    fun changedBetween(from: Long, to: Long): Collection<TileKey>? = null

    /** Tile supplying the square's sample, for overlaying pending edits at distant zoom. */
    fun representativeTile(level: Int, x: Int, z: Int): TileKey?

    /** Number of changed tiles written. */
    fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int

    fun sealIfDue(): Boolean

    fun seal()
}
