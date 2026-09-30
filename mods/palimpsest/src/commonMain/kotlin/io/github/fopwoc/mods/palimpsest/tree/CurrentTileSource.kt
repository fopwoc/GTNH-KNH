package io.github.fopwoc.mods.palimpsest.tree

/** The preserved historical root with a replaceable current layer over changed chunks. */
class CurrentTileSource(private val history: MapTree, private val current: LatestTileStore) :
    TileSource {
    override val latestEpoch: Long
        get() = maxOf(history.latestEpoch, current.latestEpoch)

    override fun tile(key: TileKey, epoch: Long): TileRecord? =
        if (epoch == Long.MAX_VALUE) current.tile(key, epoch) ?: history.tile(key, epoch)
        else history.tile(key, epoch)

    override fun tiles(
        x0: Int,
        z0: Int,
        side: Int,
        epoch: Long,
        checkActive: () -> Unit,
    ): Array<TileRecord?> {
        val tiles = history.tiles(x0, z0, side, epoch, checkActive)
        if (epoch == Long.MAX_VALUE)
            for (offset in tiles.indices) {
                checkActive()
                current.tile(TileKey(x0 + offset % side, z0 + offset / side), epoch)?.let {
                    tiles[offset] = it
                }
            }
        return tiles
    }

    override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray {
        if (epoch != Long.MAX_VALUE) return history.samples(level, x0, z0, side, epoch)
        val base = history.samples(level, x0, z0, side, epoch)
        for (offset in base.indices) {
            val x = x0 + offset % side
            val z = z0 + offset / side
            val overlay = current.representative(level, x, z) ?: continue
            if (level == 0 || base[offset] == Sample.NONE.packed) {
                base[offset] = overlay.second.packed
                continue
            }
            val previous = history.representativeTile(level, x, z)
            if (previous == null || overlay.first <= previous) base[offset] = overlay.second.packed
        }
        return base
    }

    override fun representativeTile(level: Int, x: Int, z: Int): TileKey? {
        val base = history.representativeTile(level, x, z)
        val overlay = current.representativeTile(level, x, z) ?: return base
        return if (base == null || overlay <= base) overlay else base
    }

    override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int =
        current.write(epoch, changes)

    override fun sealIfDue(): Boolean = false

    override fun seal() = Unit

    override fun close() = Unit
}
