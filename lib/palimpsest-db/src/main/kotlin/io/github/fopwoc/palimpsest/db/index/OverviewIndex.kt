package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.SampleGrid
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Far-zoom samples of a dimension, one [OverviewRegion] per region, up to [capacity] in memory. A
 * cell of 2^level chunks takes the sample of its first chunk in Z-order that existed at the moment;
 * above a region's size, its first existing region in Z-order decides the same way.
 */
internal class OverviewIndex(private val directory: Path, private val capacity: Int = 1024) {
    private val regions = LinkedHashMap<RegionKey, OverviewRegion>(256, 0.75f, true)

    /** Regions holding any sample, on disk or not yet saved. */
    private val existing: MutableSet<RegionKey> = ConcurrentHashMap.newKeySet()

    init {
        Files.createDirectories(directory)
        for (file in directory.listDirectoryEntries("r.*.ovw")) {
            val (x, z) = file.name.split('.').let { it[1].toInt() to it[2].toInt() }
            existing += RegionKey(x, z)
        }
    }

    /** Writer thread only. */
    fun append(pos: ChunkPos, tick: Long, sample: IntArray) {
        val key = RegionKey.of(pos)
        synchronized(regions) { region(key).append(RegionKey.local(pos), tick, sample) }
        existing += key
    }

    /**
     * Saves dirty regions; returns their files' lengths by name relative to the index directory.
     */
    fun flush(): Map<String, Long> {
        val dirty = synchronized(regions) { regions.values.filter { it.dirty } }
        val lengths = dirty.associate { "$NAME/${OverviewRegion.fileName(it.key)}" to it.save() }
        synchronized(regions) { evict() }
        return lengths
    }

    fun grid(window: ChunkWindow, level: Int, tick: Long): SampleGrid {
        require(level in 0..MAX_LEVEL) { "Overview level out of range: $level" }
        val x0 = Math.floorDiv(window.x0, 1 shl level)
        val z0 = Math.floorDiv(window.z0, 1 shl level)
        val width = Math.floorDiv(window.x0 + window.width - 1, 1 shl level) - x0 + 1
        val height = Math.floorDiv(window.z0 + window.height - 1, 1 shl level) - z0 + 1
        val samples = IntArray(width * height * OverviewRegion.SAMPLE)
        val present = BooleanArray(width * height)
        for (z in 0 until height) for (x in 0 until width) {
            val cell = z * width + x
            present[cell] =
                if (level <= REGION_LEVEL)
                    sampleInRegion(
                        x0 + x,
                        z0 + z,
                        level,
                        tick,
                        samples,
                        cell * OverviewRegion.SAMPLE,
                    )
                else
                    sampleAcrossRegions(
                        x0 + x,
                        z0 + z,
                        level,
                        tick,
                        samples,
                        cell * OverviewRegion.SAMPLE,
                    )
        }
        return SampleGrid(level, x0, z0, width, height, samples, present)
    }

    /** A cell no larger than a region: one contiguous run of the region's Z-order. */
    private fun sampleInRegion(
        cellX: Int,
        cellZ: Int,
        level: Int,
        tick: Long,
        into: IntArray,
        offset: Int,
    ): Boolean {
        val chunkX = cellX shl level
        val chunkZ = cellZ shl level
        val key = RegionKey.of(ChunkPos(chunkX, chunkZ))
        if (key !in existing) return false
        val region = synchronized(regions) { region(key) }
        val from = Morton.code(chunkX and 31, chunkZ and 31)
        val local = region.firstPresent(from, from + (1 shl (2 * level)), tick)
        return local >= 0 && region.sampleAt(local, tick, into, offset)
    }

    /**
     * A cell spanning regions: its existing regions in Z-order, the first one with a chunk by then.
     */
    private fun sampleAcrossRegions(
        cellX: Int,
        cellZ: Int,
        level: Int,
        tick: Long,
        into: IntArray,
        offset: Int,
    ): Boolean {
        val side = 1 shl (level - REGION_LEVEL)
        val rx0 = cellX * side
        val rz0 = cellZ * side
        val candidates =
            existing
                .filter { it.x - rx0 in 0 until side && it.z - rz0 in 0 until side }
                .sortedBy { mortonLong(it.x - rx0, it.z - rz0) }
        for (key in candidates) {
            val region = synchronized(regions) { region(key) }
            val local = region.firstPresent(0, RegionKey.CHUNKS, tick)
            if (local >= 0) return region.sampleAt(local, tick, into, offset)
        }
        return false
    }

    /** Callers hold the regions' lock. */
    private fun region(key: RegionKey): OverviewRegion =
        regions.getOrPut(key) { OverviewRegion.load(key, directory) }.also { evict() }

    private fun evict() {
        val iterator = regions.entries.iterator()
        while (regions.size > capacity && iterator.hasNext()) if (!iterator.next().value.dirty)
            iterator.remove()
    }

    companion object {
        const val NAME = "overview"

        /** A cell of 2^5 chunks is exactly one region. */
        private const val REGION_LEVEL = 5
        const val MAX_LEVEL = 20

        private fun mortonLong(x: Int, z: Int): Long {
            var code = 0L
            for (bit in 0 until 31) code =
                code or
                    (((x shr bit) and 1).toLong() shl (2 * bit)) or
                    (((z shr bit) and 1).toLong() shl (2 * bit + 1))
            return code
        }
    }
}
