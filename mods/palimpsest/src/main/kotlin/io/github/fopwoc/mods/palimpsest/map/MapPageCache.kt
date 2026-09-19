package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.util.LinkedHashMap

/**
 * Bounded tables of built pages: one for the live view, one for a single pinned historical
 * moment. Pages are built outside the lock so builds and invalidations do not block each other;
 * a build whose page was invalidated while it ran is returned but not cached. When the pinned
 * moment moves, only pages whose squares differ between the two moments (a structural diff of
 * the two roots) are dropped.
 */
class MapPageCache(private val builder: PageBuilder, private val tree: MapTree, private val maxLatestPages: Int = 128) {
    init {
        require(maxLatestPages > 0)
    }

    /** Bounded page table that remembers which in-flight builds it invalidated. */
    private inner class Table {
        val pages =
            object : LinkedHashMap<MapPageKey, MapPageRaster?>(maxLatestPages, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>): Boolean =
                    size > maxLatestPages
            }
        private val building = HashMap<MapPageKey, Int>()
        private val stale = HashSet<MapPageKey>()

        fun remove(key: MapPageKey) {
            pages.remove(key)
            if (key in building) stale += key
        }

        fun clear() {
            pages.clear()
            stale += building.keys
        }

        fun begin(key: MapPageKey) {
            building.merge(key, 1, Int::plus)
        }

        fun end(key: MapPageKey, raster: MapPageRaster?, store: Boolean) {
            val remaining = building.merge(key, -1, Int::plus) ?: 0
            if (remaining <= 0) building.remove(key)
            if (store && key !in stale) pages[key] = raster
            if (remaining <= 0) stale.remove(key)
        }
    }

    private val lock = Any()
    private val latest = Table()
    private val historical = Table()
    private var historicalEpoch: Long? = null

    /** Drops latest pages of every tile and historical pages only when they can be affected. */
    fun invalidateTiles(tiles: Collection<TileKey>, earliestEpoch: Long) =
        synchronized(lock) {
            val historyAffected = historicalEpoch?.let { earliestEpoch <= it } ?: false
            for (tile in tiles) {
                removePages(latest, tile)
                if (historyAffected) removePages(historical, tile)
            }
        }

    private fun removePages(table: Table, tile: TileKey) {
        for (lod in 0..MapPageKey.MAX_LOD) table.remove(MapPageKey.containingTile(tile.x, tile.z, lod))
    }

    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? {
        synchronized(lock) {
            if (latest.pages.containsKey(key)) return latest.pages[key]
            latest.begin(key)
        }
        return buildTracked(latest, key, Long.MAX_VALUE, checkActive) { true }
    }

    /** Keeps one historical time and drops only the pages that differ when it moves. */
    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
        require(epoch >= 0)
        checkActive()
        synchronized(lock) {
            val previous = historicalEpoch
            if (previous != epoch) {
                if (previous != null) advanceHistorical(previous, epoch) else historical.clear()
                historicalEpoch = epoch
            }
            if (historical.pages.containsKey(key)) return historical.pages[key]
            historical.begin(key)
        }
        return buildTracked(historical, key, epoch, checkActive) { historicalEpoch == epoch }
    }

    fun cachedLatestPages(): Int = synchronized(lock) { latest.pages.size }

    fun cachedHistoricalPages(): Int = synchronized(lock) { historical.pages.size }

    fun clear() =
        synchronized(lock) {
            latest.clear()
            historical.clear()
            historicalEpoch = null
        }

    private inline fun buildTracked(
        table: Table,
        key: MapPageKey,
        epoch: Long,
        noinline checkActive: () -> Unit,
        stillWanted: () -> Boolean,
    ): MapPageRaster? {
        var raster: MapPageRaster? = null
        var built = false
        try {
            raster = builder.build(key, epoch, checkActive)
            built = true
        } finally {
            synchronized(lock) { table.end(key, raster, built && stillWanted()) }
        }
        return raster
    }

    private fun advanceHistorical(from: Long, to: Long) {
        for (key in historical.pages.keys.toList()) {
            if (changedBetween(key, from, to)) historical.remove(key)
        }
    }

    /** Whether any square under the page differs between the two moments. */
    private fun changedBetween(key: MapPageKey, from: Long, to: Long): Boolean {
        val level = (key.lod - PageBuilder.TILE_LOD).coerceAtLeast(0)
        val squaresPerPage = MapPageKey.SIDE shr (PageBuilder.TILE_LOD - key.lod).coerceAtLeast(0)
        val x0 = key.x * squaresPerPage + (MapTree.OFFSET ushr level)
        val z0 = key.z * squaresPerPage + (MapTree.OFFSET ushr level)
        return tree.changed(from, to, level, x0, z0, squaresPerPage).any { it }
    }
}
