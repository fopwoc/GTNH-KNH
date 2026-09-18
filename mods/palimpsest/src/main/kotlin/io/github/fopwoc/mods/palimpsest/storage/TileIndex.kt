package io.github.fopwoc.mods.palimpsest.storage

import java.util.LinkedHashMap

/**
 * Per-store tile directory: resident histories plus a cold sidecar consulted on demand.
 *
 * Tiles appended since the last save are dirty and pinned; clean tiles beyond the resident byte
 * budget are evicted least-recently-used and reloaded from the sidecar when touched again.
 */
internal class TileIndex(private val residentBudgetBytes: Long) {
    private var cold: TileIndexCache? = null
    private val resident = LinkedHashMap<TileKey, PackedTileHistory>(256, 0.75f, true)
    private val dirty = HashSet<TileKey>()
    private val sizes = HashMap<TileKey, Long>()

    var recordCount = 0L
        private set

    var latestEpoch = 0L
        private set

    val tileCount: Int
        get() {
            val directory = cold?.directory ?: return resident.size
            return directory.size + resident.keys.count { it !in directory }
        }

    var residentBytes = 0L
        private set

    val residentTiles: Int
        get() = resident.size

    /** True when a sidecar backs this index, whether or not anything was appended since. */
    val hasCold: Boolean
        get() = cold != null

    fun adoptCold(cache: TileIndexCache) {
        check(resident.isEmpty() && cold == null)
        cold = cache
        recordCount = cache.recordCount
        latestEpoch = cache.latestEpoch
    }

    fun contains(key: TileKey): Boolean =
        key in resident || cold?.directory?.containsKey(key) == true

    fun lastEpoch(key: TileKey): Long =
        resident[key]?.lastEpoch ?: cold?.directory?.get(key)?.lastEpoch ?: -1L

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
        history.finishReload()
        resident[key] = history
        track(key, history)
        evict()
        return history
    }

    /** Records parsed straight from segments; call [finishReload] once all are added. */
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

    fun finishReload() {
        // Access-ordered map: collect first so tracking does not reorder during iteration.
        for ((key, history) in resident.entries.toList()) {
            history.finishReload()
            track(key, history)
        }
    }

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

    /** Every tile's records for a sidecar save, copying untouched blocks from the current one. */
    fun blocks(): Sequence<TileIndexCache.Block> = sequence {
        val cache = cold
        if (cache != null) {
            for ((key, entry) in cache.directory) {
                if (key in dirty) continue
                yield(
                    resident[key]?.let { TileIndexCache.Block.Resident(key, it) }
                        ?: TileIndexCache.Block.Cached(key, entry, cache)
                )
            }
        }
        for ((key, history) in resident) {
            if (cache == null || key in dirty) yield(TileIndexCache.Block.Resident(key, history))
        }
    }

    /** Switches to a freshly saved sidecar; resident tiles become clean and evictable. */
    fun saved(cache: TileIndexCache?) {
        cold?.close()
        cold = cache
        dirty.clear()
        if (cache != null) evict()
    }

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
