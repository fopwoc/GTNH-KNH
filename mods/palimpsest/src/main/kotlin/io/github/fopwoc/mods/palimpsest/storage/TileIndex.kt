package io.github.fopwoc.mods.palimpsest.storage

import java.util.LinkedHashMap

/**
 * Per-store tile directory: resident histories plus a cold sidecar consulted on demand.
 *
 * Tiles appended since the last save are dirty and pinned; clean tiles beyond the resident byte
 * budget are evicted least-recently-used and reloaded from the sidecar when touched again.
 *
 * The store lets many readers in at once, and even a lookup touches the LRU order here, so every
 * method is synchronized on the index itself; the histories it hands out are only mutated under the
 * store's exclusive lock.
 */
internal class TileIndex(private val residentBudgetBytes: Long) {
    private var cold: TileIndexCache? = null
    private val resident = LinkedHashMap<TileKey, PackedTileHistory>(256, 0.75f, true)
    private val dirty = HashSet<TileKey>()
    private val sizes = HashMap<TileKey, Long>()

    /** Orders segments for duplicate-epoch resolution; see [PackedTileHistory.finishReload]. */
    var segmentRank: (Int) -> Int = { it }

    var recordCount = 0L
        private set

    var latestEpoch = 0L
        private set

    val tileCount: Int
        @Synchronized
        get() {
            val directory = cold?.directory ?: return resident.size
            return directory.size + resident.keys.count { it !in directory }
        }

    var residentBytes = 0L
        @Synchronized get
        private set

    val residentTiles: Int
        @Synchronized get() = resident.size

    @Synchronized
    fun adoptCold(cache: TileIndexCache) {
        check(resident.isEmpty() && cold == null)
        cold = cache
        recordCount = cache.recordCount
        latestEpoch = cache.latestEpoch
    }

    @Synchronized
    fun contains(key: TileKey): Boolean =
        key in resident || cold?.directory?.containsKey(key) == true

    @Synchronized
    fun lastEpoch(key: TileKey): Long =
        resident[key]?.lastEpoch ?: cold?.directory?.get(key)?.lastEpoch ?: -1L

    @Synchronized
    fun history(key: TileKey): PackedTileHistory? {
        resident[key]?.let {
            return it
        }
        val cache = cold ?: return null
        if (key !in cache.directory) return null
        val history = PackedTileHistory.forReload()
        cache.load(key) { _, epoch, segment, offset, length, coverage, kind ->
            history.add(epoch, segment, offset, length, coverage, kind)
        }
        history.finishReload(segmentRank)
        resident[key] = history
        track(key, history)
        evict()
        return history
    }

    /** Records parsed straight from segments; call [finishReload] once all are added. */
    @Synchronized
    fun addParsed(
        key: TileKey,
        epoch: Long,
        segment: Int,
        offset: Long,
        length: Int,
        coverage: LongArray,
        kind: Int,
    ) {
        check(cold == null)
        resident
            .getOrPut(key, PackedTileHistory::forReload)
            .add(epoch, segment, offset, length, coverage, kind)
        dirty += key
        recordCount++
        latestEpoch = maxOf(latestEpoch, epoch)
    }

    /** Returns how many duplicate layers were dropped. */
    @Synchronized
    fun finishReload(): Int {
        var dropped = 0
        // Access-ordered map: collect first so tracking does not reorder during iteration.
        for ((key, history) in resident.entries.toList()) {
            dropped += history.finishReload(segmentRank)
            track(key, history)
        }
        recordCount -= dropped
        return dropped
    }

    @Synchronized
    fun append(
        key: TileKey,
        epoch: Long,
        segment: Int,
        offset: Long,
        length: Int,
        coverage: LongArray,
        kind: Int,
    ) {
        val history = history(key) ?: PackedTileHistory.forAppend().also { resident[key] = it }
        history.add(epoch, segment, offset, length, coverage, kind)
        track(key, history)
        dirty += key
        recordCount++
        latestEpoch = maxOf(latestEpoch, epoch)
    }

    /**
     * Re-points a run of a tile's records, e.g. from a log to the segment they were sealed into.
     */
    @Synchronized
    fun replaceRange(key: TileKey, from: Int, to: Int, replacement: List<PackedTileHistory.Entry>) {
        val history = checkNotNull(history(key))
        history.replaceRange(from, to, replacement)
        track(key, history)
        dirty += key
        recordCount += replacement.size - (to - from)
    }

    /**
     * Swaps in a history rebuilt from a snapshot taken when the tile had [snapshotSize] records;
     * records appended since then are carried over.
     */
    @Synchronized
    fun replaceTileBuilt(key: TileKey, built: PackedTileHistory, snapshotSize: Int) {
        val current = checkNotNull(history(key))
        for (entry in snapshotSize until current.size) {
            val e = current.entryAt(entry)
            built.add(e.epoch, e.segment, e.offset, e.length, e.mask, e.kind)
        }
        recordCount += built.size - current.size
        resident[key] = built
        track(key, built)
        dirty += key
    }

    /** Every tile's records for a sidecar save, copying untouched blocks from the current one. */
    @Synchronized
    fun blocks(): List<TileIndexCache.Block> {
        val blocks = ArrayList<TileIndexCache.Block>(tileCount)
        val cache = cold
        if (cache != null) {
            for ((key, entry) in cache.directory) {
                if (key in dirty) continue
                blocks +=
                    resident[key]?.let { TileIndexCache.Block.Resident(key, it) }
                        ?: TileIndexCache.Block.Cached(key, entry, cache)
            }
        }
        for ((key, history) in resident.entries.toList()) {
            if (cache == null || key in dirty) blocks += TileIndexCache.Block.Resident(key, history)
        }
        return blocks
    }

    /** Switches to a freshly saved sidecar; resident tiles become clean and evictable. */
    @Synchronized
    fun saved(cache: TileIndexCache?) {
        cold?.close()
        cold = cache
        dirty.clear()
        if (cache != null) evict()
    }

    @Synchronized
    fun clear() {
        cold?.close()
        cold = null
        resident.clear()
        dirty.clear()
        sizes.clear()
        residentBytes = 0
        recordCount = 0
        latestEpoch = 0
    }

    private fun track(key: TileKey, history: PackedTileHistory) {
        val bytes = history.arrayBytes
        residentBytes += bytes - (sizes.put(key, bytes) ?: 0L)
    }

    private fun evict() {
        if (cold == null || residentBytes <= residentBudgetBytes) return
        val iterator = resident.keys.iterator()
        while (residentBytes > residentBudgetBytes && iterator.hasNext()) {
            val key = iterator.next()
            if (key in dirty) continue
            residentBytes -= sizes.remove(key) ?: 0L
            iterator.remove()
        }
    }
}
