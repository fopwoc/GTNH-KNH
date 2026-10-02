package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.palimpsest.storage.StorageWrites
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

/** Compressed region-indexed current tiles, with a sample pyramid for distant zoom. */
class LatestTileStore(
    val directory: Path,
    private val machineId: Int,
    private val translateBlock: (machine: Int, id: Int) -> Int = { _, id -> id },
    val writes: StorageWrites = StorageWrites(),
    /** Zero retains the preceding per-region, per-batch force policy. */
    private val flushBytes: Long = DEFAULT_FLUSH_BYTES,
    private val maxPendingRegions: Int = DEFAULT_PENDING_REGIONS,
) : TileSource {
    private data class Entry(
        val machine: Int,
        val epoch: Long,
        val sample: Sample,
        val region: LatestRegion,
    )

    private data class Representative(val tile: TileKey, val sample: Sample)

    private val pendingRegions = LinkedHashSet<LatestRegion>()
    private var pendingBytes = 0L
    private val regions = ConcurrentHashMap<Pair<Int, Int>, LatestRegion>()
    private val entries = ConcurrentHashMap<TileKey, Entry>()
    private val samples = List(MapTree.LEVELS) { ConcurrentHashMap<Long, Representative>() }
    private val lock: FileLock

    @Volatile
    override var latestEpoch: Long = -1
        private set

    init {
        require(flushBytes >= 0 && maxPendingRegions > 0)
        Files.createDirectories(directory)
        val ignore = directory.resolve(".gitignore")
        if (!Files.exists(ignore)) Files.writeString(ignore, "latest.lock\n*.tmp\n")
        val channel =
            FileChannel.open(
                directory.resolve("latest.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        val acquired =
            try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
        if (acquired == null) {
            channel.close()
            throw MapInUseException("$directory is open in another game")
        }
        lock = acquired
        loadEntries()
    }

    @Suppress("TooGenericExceptionCaught") // Any failed load must release the acquired lock.
    private fun loadEntries() {
        try {
            Files.walk(directory).use { files ->
                files
                    .filter {
                        if (it.name.endsWith(".tile"))
                            throw CorruptTreeException(
                                "Old current-tile format in $directory; rebuild the map"
                            )
                        it.name.endsWith(SUFFIX)
                    }
                    .forEach { path ->
                        val coordinates = parseRegion(path)
                        val region = LatestRegion(path, writes)
                        regions[coordinates] = region
                        for ((slot, stored) in region.entries()) {
                            val key =
                                TileKey(
                                    coordinates.first * REGION + slot % REGION,
                                    coordinates.second * REGION + slot / REGION,
                                )
                            val sample = stored.sample
                            val translated =
                                Sample(
                                    translateBlock(stored.machine, sample.block),
                                    sample.height,
                                    sample.depth,
                                    sample.biome,
                                )
                            val entry = Entry(stored.machine, stored.epoch, translated, region)
                            entries[key] = entry
                            remember(key, translated)
                            latestEpoch = maxOf(latestEpoch, entry.epoch)
                        }
                    }
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    override fun tile(key: TileKey, epoch: Long): TileRecord? {
        require(epoch == Long.MAX_VALUE) { "Latest-only storage has no historical snapshots" }
        val entry = entries[key] ?: return null
        return entry.region.tile(slot(key), translateBlock)
    }

    override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long): LongArray {
        require(epoch == Long.MAX_VALUE && level in 0 until MapTree.LEVELS && side > 0)
        val levelSamples = samples[level]
        return LongArray(side * side) { offset ->
            levelSamples[squareId(x0 + offset % side, z0 + offset / side)]?.sample?.packed
                ?: Sample.NONE.packed
        }
    }

    fun representative(level: Int, x: Int, z: Int): Pair<TileKey, Sample>? =
        samples[level][squareId(x, z)]?.let { it.tile to it.sample }

    override fun representativeTile(level: Int, x: Int, z: Int): TileKey? =
        representative(level, x, z)?.first

    fun keys(): Set<TileKey> = entries.keys.toSet()

    fun diskBytes(): Long =
        regions.values.sumOf { if (Files.exists(it.path)) Files.size(it.path) else 0L }

    @Synchronized
    fun clear() {
        for (region in regions.values) region.delete()
        regions.clear()
        pendingRegions.clear()
        pendingBytes = 0
        entries.clear()
        samples.forEach(MutableMap<Long, Representative>::clear)
        latestEpoch = -1
    }

    @Synchronized
    override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int {
        require(epoch > latestEpoch)
        val changed = changes.filter { (key, record) ->
            require(record.epoch == epoch)
            tile(key, Long.MAX_VALUE)?.sameFacts(record) != true
        }
        for ((coordinates, batch) in changed.entries.groupBy { coordinates(it.key) }) {
            val region =
                regions.getOrPut(coordinates) {
                    LatestRegion(
                        directory.resolve("${coordinates.first}_${coordinates.second}$SUFFIX"),
                        writes,
                    )
                }
            val before = region.unflushedBytes
            try {
                region.write(
                    machineId,
                    batch.associate { slot(it.key) to it.value },
                    flushBytes == 0L,
                )
            } finally {
                pendingBytes += region.unflushedBytes - before
                if (region.unflushedBytes > 0) pendingRegions += region
                else pendingRegions -= region
            }
            if (pendingBytes >= flushBytes || pendingRegions.size >= maxPendingRegions) seal()
            for ((key, record) in batch) {
                entries[key] = Entry(machineId, epoch, record.sample, region)
                remember(key, record.sample)
            }
        }
        latestEpoch = epoch
        return changed.size
    }

    private fun remember(key: TileKey, sample: Sample) {
        for (level in samples.indices) {
            val square = squareId(MapTree.squareX(key, level), MapTree.squareZ(key, level))
            samples[level].compute(square) { _, current ->
                if (current == null || key == current.tile || key < current.tile)
                    Representative(key, sample)
                else current
            }
        }
    }

    private fun coordinates(key: TileKey): Pair<Int, Int> =
        Math.floorDiv(key.x, REGION) to Math.floorDiv(key.z, REGION)

    private fun slot(key: TileKey): Int =
        Math.floorMod(key.z, REGION) * REGION + Math.floorMod(key.x, REGION)

    private fun parseRegion(path: Path): Pair<Int, Int> {
        val parts = path.name.removeSuffix(SUFFIX).split('_')
        if (parts.size != 2) throw CorruptTreeException("Invalid latest region name $path")
        return parts[0].toInt() to parts[1].toInt()
    }

    /** Called by the map's existing maintenance pass, including when observations are idle. */
    @Synchronized
    override fun sealIfDue(): Boolean {
        val dirty = pendingRegions.isNotEmpty()
        seal()
        return dirty
    }

    @Synchronized
    override fun seal() {
        val iterator = pendingRegions.iterator()
        while (iterator.hasNext()) {
            val region = iterator.next()
            val before = region.unflushedBytes
            region.flush()
            pendingBytes -= before
            iterator.remove()
        }
    }

    @Synchronized
    override fun close() {
        try {
            var failure: IOException? = null
            for (region in regions.values) {
                try {
                    region.close()
                } catch (error: IOException) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        } finally {
            lock.channel().close()
        }
    }

    companion object {
        const val DEFAULT_FLUSH_BYTES = 1L shl 20
        const val DEFAULT_PENDING_REGIONS = 32
        private const val REGION = 32
        private const val SUFFIX = ".preg"

        private fun squareId(x: Int, z: Int): Long =
            (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
