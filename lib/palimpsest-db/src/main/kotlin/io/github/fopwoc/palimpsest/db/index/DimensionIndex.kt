package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.Commit
import java.nio.file.Files
import java.nio.file.Path

/**
 * The rebuildable, local half of a dimension: chunk versions by region, the timeline and how much
 * truth they cover. Regions load on demand, outside any lock, and the least recently used clean
 * ones are dropped beyond [capacity], so memory follows the area in use, not the size of the world.
 * Reads are thread-safe; appends and flushes belong to the writer thread.
 */
internal class DimensionIndex
private constructor(
    private val directory: Path,
    private val capacity: Int,
    coverage: Coverage?,
    private val timeline: TimelineFile,
) {
    /** Called once with each loaded region the commit pipeline touches, under the regions' lock. */
    @Volatile var onLoadForWrite: (RegionIndex) -> Unit = {}

    /** In access order; guarded by itself. */
    private val regions = LinkedHashMap<RegionKey, RegionIndex>(capacity * 2, 0.75f, true)

    /** Truth bytes applied, per segment in truth order; writer thread only. */
    val covered: MutableList<Coverage.Segment> = coverage?.segments.orEmpty().toMutableList()

    /** Length of every index file as of the last flush; writer thread only. */
    private val files: MutableMap<String, Long> = coverage?.files.orEmpty().toMutableMap()

    val commits: List<Commit>
        get() = timeline.commits

    /** The chunk's latest version, for the commit pipeline to compare against. */
    fun latest(pos: ChunkPos): LongArray? =
        region(RegionKey.of(pos), forWrite = true).latest(RegionKey.local(pos))

    fun at(pos: ChunkPos, tick: Long): LongArray? =
        region(RegionKey.of(pos)).at(RegionKey.local(pos), tick)

    /** Under the map's lock, so the region cannot be dropped between being found and written. */
    fun append(pos: ChunkPos, version: LongArray) {
        val key = RegionKey.of(pos)
        region(key)
        synchronized(regions) { region(key).append(RegionKey.local(pos), version) }
    }

    fun append(commit: Commit) = timeline.append(commit)

    /** Records that segment [ordinal] named [name] is applied up to [length]. */
    fun cover(ordinal: Int, name: String, length: Long) {
        val entry = Coverage.Segment(name, length)
        if (ordinal < covered.size) covered[ordinal] = entry else covered += entry
    }

    /** Saves every dirty region and the timeline, then the coverage that commits them. */
    fun flush() {
        val dirty = synchronized(regions) { regions.values.filter { it.dirty } }
        for (region in dirty) files["$REGIONS/${region.key.fileName}"] = region.save()
        files[TIMELINE] = timeline.save()
        Coverage(covered.toList(), files.toMap()).write(directory)
        synchronized(regions) { evict(keep = null) }
    }

    private fun region(key: RegionKey, forWrite: Boolean = false): RegionIndex {
        synchronized(regions) { regions[key]?.also { if (forWrite) introduce(it) } }
            ?.let {
                return it
            }
        val loaded = RegionIndex.load(key, directory.resolve(REGIONS))
        synchronized(regions) {
            // Another thread may have loaded it meanwhile; its copy wins, ours was never written
            // to.
            regions[key]?.let {
                if (forWrite) introduce(it)
                return it
            }
            regions[key] = loaded
            if (forWrite) introduce(loaded)
            evict(keep = key)
        }
        return loaded
    }

    /** Hands [region] to [onLoadForWrite] the first time the commit pipeline touches it. */
    private fun introduce(region: RegionIndex) {
        if (region.seenByWriter) return
        region.seenByWriter = true
        onLoadForWrite(region)
    }

    /** Drops least recently used regions beyond [capacity]; unsaved versions pin a region. */
    private fun evict(keep: RegionKey?) {
        val iterator = regions.entries.iterator()
        while (regions.size > capacity && iterator.hasNext()) {
            val (key, region) = iterator.next()
            if (key != keep && !region.dirty) iterator.remove()
        }
    }

    companion object {
        private const val REGIONS = "regions"
        private const val TIMELINE = "timeline"

        /** Opens the index in [directory]; [fresh] discards whatever is there first. */
        fun open(directory: Path, capacity: Int, fresh: Boolean): DimensionIndex {
            if (fresh) directory.toFile().deleteRecursively()
            Files.createDirectories(directory.resolve(REGIONS))
            val coverage = if (fresh) null else Coverage.read(directory)
            return DimensionIndex(
                directory,
                capacity,
                coverage,
                TimelineFile.load(directory.resolve(TIMELINE)),
            )
        }

        /** The stored coverage if every file it vouches for is still whole. */
        fun coverage(directory: Path): Coverage? =
            Coverage.read(directory)?.takeIf { it.intact(directory) }
    }
}
