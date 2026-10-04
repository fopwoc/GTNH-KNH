package io.github.fopwoc.mods.palimpsest.tree

/** Where the map's pages read tiles and far-zoom squares from, at an epoch. */
interface TileSource {
    val latestEpoch: Long

    fun tile(key: TileKey, epoch: Long): TileRecord?

    /** A square window of tiles in one read. */
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

    /**
     * [side]×[side] packed [Sample]s of level-[level] squares, each 2^level tiles across, starting
     * at square ([x0], [z0]); a square's coordinates are its first tile's shifted right by [level].
     */
    fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray

    /**
     * Tiles whose look differs between epochs [from] ≤ [to], or null when this source cannot tell.
     */
    fun changedBetween(from: Long, to: Long): Collection<TileKey>? = null
}
