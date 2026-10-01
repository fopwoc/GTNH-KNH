package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.PageSnapshot
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.util.LinkedHashMap

/**
 * Bounded tables of built pages: one for the live view, one for a single pinned historical moment.
 * Pages are built outside the lock so builds and invalidations do not block each other; a build
 * whose page was invalidated while it ran is returned but not cached. When the pinned moment moves,
 * only pages whose squares differ between the two moments (a structural diff of the two roots) are
 * marked dirty. Previous inputs remain bounded by the same LRU for immutable partial refreshes.
 */
class MapPageCache(
    private val builder: PageBuilder,
    private val tree: MapTree?,
    private val maxLatestPages: Int = 128,
) {
    init {
        require(maxLatestPages > 0)
    }

    /** Bounded page table that remembers which in-flight builds it invalidated. */
    private inner class Table {
        inner class Entry(val snapshot: PageSnapshot) {
            var dirty = false
            var fullBuild = false
            var tiles: MutableSet<TileKey>? = LinkedHashSet()
        }

        // Invalidation must not advance the access order of an offscreen page.
        private val byKey = HashMap<MapPageKey, Entry>()
        val pages =
            object : LinkedHashMap<MapPageKey, Entry>(maxLatestPages, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<MapPageKey, Entry>
                ): Boolean {
                    if (size <= maxLatestPages) return false
                    byKey.remove(eldest.key)
                    return true
                }
            }
        private val building = HashMap<MapPageKey, Int>()
        private val stale = HashSet<MapPageKey>()

        fun invalidate(key: MapPageKey, tile: TileKey? = null) {
            byKey[key]?.let { entry ->
                entry.dirty = true
                if (tile == null) entry.tiles = null
                else
                    entry.tiles?.let { tiles ->
                        tiles.add(tile)
                        if (tiles.size > MAX_DIRTY_TILES) {
                            entry.tiles = null
                            entry.fullBuild = true
                        }
                    }
            }
            if (key in building) stale += key
        }

        fun clear() {
            pages.clear()
            byKey.clear()
            stale += building.keys
        }

        fun begin(key: MapPageKey) {
            building.merge(key, 1, Int::plus)
        }

        fun end(key: MapPageKey, snapshot: PageSnapshot?, store: Boolean) {
            val remaining = building.merge(key, -1, Int::plus) ?: 0
            if (remaining <= 0) building.remove(key)
            if (store && key !in stale) {
                val entry = Entry(checkNotNull(snapshot))
                byKey[key] = entry
                pages[key] = entry
            }
            if (remaining <= 0) stale.remove(key)
        }
    }

    private val lock = Any()
    private val latest = Table()
    private val historical = Table()
    private var historicalEpoch: Long? = null

    /** Marks latest pages dirty, and historical pages only when they can be affected. */
    fun invalidateTiles(tiles: Collection<TileKey>, earliestEpoch: Long) =
        synchronized(lock) {
            val historyAffected = historicalEpoch?.let { earliestEpoch <= it } ?: false
            for (tile in tiles) {
                removePages(latest, tile)
                if (historyAffected) removePages(historical, tile)
            }
        }

    private fun removePages(table: Table, tile: TileKey) {
        for (page in MapPageKey.affectedBy(tile)) table.invalidate(page, tile)
    }

    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? {
        val input: BuildInput
        synchronized(lock) {
            val entry = latest.pages[key]
            if (entry != null && !entry.dirty) return entry.snapshot.raster
            input = BuildInput(entry?.takeUnless { it.fullBuild }?.snapshot, entry?.tiles?.toSet())
            latest.begin(key)
        }
        return buildTracked(latest, key, Long.MAX_VALUE, input, checkActive) { true }
    }

    /** Keeps one historical time and refreshes only the pages that differ when it moves. */
    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
        require(epoch >= 0)
        checkActive()
        val input: BuildInput
        synchronized(lock) {
            val previous = historicalEpoch
            if (previous != epoch) {
                if (previous != null) advanceHistorical(previous, epoch) else historical.clear()
                historicalEpoch = epoch
            }
            val entry = historical.pages[key]
            if (entry != null && !entry.dirty) return entry.snapshot.raster
            input = BuildInput(entry?.takeUnless { it.fullBuild }?.snapshot, entry?.tiles?.toSet())
            historical.begin(key)
        }
        return buildTracked(historical, key, epoch, input, checkActive) { historicalEpoch == epoch }
    }

    fun cachedLatestPages(): Int = synchronized(lock) { latest.pages.values.count { !it.dirty } }

    fun cachedHistoricalPages(): Int =
        synchronized(lock) { historical.pages.values.count { !it.dirty } }

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
        input: BuildInput,
        noinline checkActive: () -> Unit,
        stillWanted: () -> Boolean,
    ): MapPageRaster? {
        var snapshot: PageSnapshot? = null
        var built = false
        try {
            snapshot = builder.rebuild(key, epoch, input.previous, input.tiles, checkActive)
            built = true
        } finally {
            synchronized(lock) { table.end(key, snapshot, built && stillWanted()) }
        }
        return snapshot?.raster
    }

    private fun advanceHistorical(from: Long, to: Long) {
        for (key in historical.pages.keys.toList()) {
            if (changedBetween(key, from, to)) historical.invalidate(key)
        }
    }

    /** Whether any square under the page differs between the two moments. */
    private fun changedBetween(key: MapPageKey, from: Long, to: Long): Boolean {
        val level = (key.lod - PageBuilder.TILE_LOD).coerceAtLeast(0)
        val squaresPerPage = MapPageKey.SIDE shr (PageBuilder.TILE_LOD - key.lod).coerceAtLeast(0)
        val x0 = key.x * squaresPerPage + (MapTree.OFFSET ushr level)
        val z0 = key.z * squaresPerPage + (MapTree.OFFSET ushr level)
        val changed =
            checkNotNull(tree).changed(from, to, level, x0 - 1, z0 - 1, squaresPerPage + 1)
        // The north-west corner itself is never sampled by the shader; the two strips are.
        return (1 until changed.size).any { changed[it] }
    }

    private class BuildInput(val previous: PageSnapshot?, val tiles: Set<TileKey>?)

    companion object {
        private const val MAX_DIRTY_TILES = 32
    }
}
