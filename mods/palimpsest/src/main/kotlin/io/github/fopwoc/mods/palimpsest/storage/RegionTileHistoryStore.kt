package io.github.fopwoc.mods.palimpsest.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * Opens only the immutable segment indexes needed by the current working regions.
 *
 * The read/write lock guards only the table of open regions: reads, appends and maintenance hold it
 * shared, opening or evicting a region holds it exclusively, so a region is never closed under an
 * in-flight operation. Appenders are additionally serialized by [appendLock] so cross-region
 * validation and the appends it protects cannot interleave.
 */
class RegionTileHistoryStore(
    private val directory: Path,
    private val maxOpenRegions: Int = DEFAULT_OPEN_REGIONS,
    private val indexCacheEnabled: Boolean = true,
) : AutoCloseable {
    init {
        require(maxOpenRegions > 0)
    }

    private data class Region(val x: Int, val z: Int)

    private class Open(val store: TileHistoryStore, @Volatile var lastUsed: Long)

    private val lock = ReentrantReadWriteLock()
    private val appendLock = ReentrantLock()
    private val clock = AtomicLong()
    private val open = HashMap<Region, Open>()
    private var opened = 0L
    private var evicted = 0L
    private var indexCacheHits = 0L
    private var segmentsHashed = 0L

    fun read(key: TileKey, epoch: Long): TileHistoryStore.TileRead? =
        withRegion(key) { it.read(key, epoch) }

    fun readPixel(key: TileKey, epoch: Long, position: Int): Int? =
        withRegion(key) { it.readPixel(key, epoch, position) }

    fun readSamples(key: TileKey, epoch: Long, positions: IntArray): TileHistoryStore.SampleRead? =
        withRegion(key) { it.readSamples(key, epoch, positions) }

    fun hasChanges(key: TileKey, firstEpoch: Long, secondEpoch: Long): Boolean =
        withRegion(key) { it.hasChanges(key, firstEpoch, secondEpoch) }

    /** Validates every region's batch before the first layer is logged. */
    fun append(layers: List<TileLayer>): TileHistoryStore.AppendResult = appendLock.withLock {
        ensureGitignore()
        val batches = layers.groupBy { regionOf(it.key) }
        val stores = batches.keys.associateWith { region -> withRegion(region) { it } }
        for ((region, batch) in batches) stores.getValue(region).validateEpochs(batch)
        var written = 0
        var discarded = 0
        var covered = 0
        var bytes = 0L
        for ((region, batch) in batches) {
            val result = stores.getValue(region).append(batch)
            written += result.layersWritten
            discarded += result.layersDiscarded
            covered += result.coveredCells
            bytes += result.bytesAdded
        }
        TileHistoryStore.AppendResult(written, discarded, covered, bytes)
    }

    /** Seals pending logs and persists dirty sidecars so eviction on the read path stays cheap. */
    fun flush(): Unit = openStores().forEach(TileHistoryStore::flush)

    /** Seals every region whose log is due; cheap when nothing is. Returns regions sealed. */
    fun sealDue(): Int = openStores().count(TileHistoryStore::sealIfDue)

    /** Merges small segments in every open region; returns how many regions were compacted. */
    fun compact(): Int = openStores().count(TileHistoryStore::compact)

    /** Maintenance works on a snapshot; a store evicted meanwhile finishes its own work first. */
    private fun openStores(): List<TileHistoryStore> = lock.read { open.values.map { it.store } }

    /** Only sealed segments are map data; logs, sidecars and temp files stay on this machine. */
    private fun ensureGitignore() {
        val ignore = directory.resolve(".gitignore")
        if (Files.exists(ignore)) return
        Files.createDirectories(directory)
        Files.writeString(ignore, "*.wal\n*.pidx\n*.tmp\n")
    }

    override fun close(): Unit = lock.write {
        open.values.forEach { it.store.close() }
        open.clear()
    }

    fun openRegionCount(): Int = lock.read { open.size }

    fun regionOpenCount(): Long = lock.read { opened }

    fun regionEvictionCount(): Long = lock.read { evicted }

    fun indexCacheHitCount(): Long = lock.read { indexCacheHits }

    fun segmentsHashedCount(): Long = lock.read { segmentsHashed }

    fun openIndexArrayBytes(): Long = lock.read { open.values.sumOf { it.store.indexArrayBytes } }

    private inline fun <T> withRegion(key: TileKey, action: (TileHistoryStore) -> T): T =
        withRegion(regionOf(key), action)

    private inline fun <T> withRegion(region: Region, action: (TileHistoryStore) -> T): T {
        val readLock = lock.readLock()
        val writeLock = lock.writeLock()
        readLock.lock()
        try {
            open[region]?.let {
                return action(it.touch().store)
            }
        } finally {
            readLock.unlock()
        }
        writeLock.lock()
        val store: TileHistoryStore
        try {
            store = openLocked(region).store
            // Downgrade so other readers proceed while this read runs.
            readLock.lock()
        } finally {
            writeLock.unlock()
        }
        try {
            return action(store)
        } finally {
            readLock.unlock()
        }
    }

    private fun Open.touch(): Open = also { lastUsed = clock.incrementAndGet() }

    private fun openLocked(region: Region): Open {
        open[region]?.let {
            return it.touch()
        }
        if (open.size >= maxOpenRegions) {
            val eldest = open.entries.minBy { it.value.lastUsed }
            eldest.value.store.close()
            open.remove(eldest.key)
            evicted++
        }
        val store =
            TileHistoryStore(directory.resolve("${region.x}_${region.z}"), indexCacheEnabled)
        opened++
        if (store.loadedFromIndexCache) indexCacheHits++
        segmentsHashed += store.segmentsHashed
        return Open(store, clock.incrementAndGet()).also { open[region] = it }
    }

    private fun regionOf(key: TileKey) =
        Region(Math.floorDiv(key.x, REGION_TILES), Math.floorDiv(key.z, REGION_TILES))

    companion object {
        const val REGION_TILES = 32
        /** A far-zoom page samples one tile per region; 512 keeps a whole screen of them open. */
        const val DEFAULT_OPEN_REGIONS = 512
    }
}
