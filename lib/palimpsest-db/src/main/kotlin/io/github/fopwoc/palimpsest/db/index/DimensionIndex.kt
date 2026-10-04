package io.github.fopwoc.palimpsest.db.index

import io.github.fopwoc.palimpsest.db.ChunkDiff
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkWindow
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
        get() = timeline.commits()

    val overview = OverviewIndex(directory.resolve(OverviewIndex.NAME))

    /** The chunk's latest version, for the commit pipeline to compare against. */
    fun latest(pos: ChunkPos): LongArray? =
        region(RegionKey.of(pos), forWrite = true).latest(RegionKey.local(pos))

    fun at(pos: ChunkPos, tick: Long): LongArray? =
        region(RegionKey.of(pos)).at(RegionKey.local(pos), tick)

    /**
     * Resolves delta bases for blobs of [pos]'s chunk: a delta's base is always an earlier version
     * of the same chunk, so it lives in the same region.
     */
    fun bases(pos: ChunkPos): (Long) -> LongArray? = region(RegionKey.of(pos))::baseOf

    /** Deltas between the blob at [position] of [pos]'s chunk and a full section. */
    fun depth(pos: ChunkPos, position: Long): Int = region(RegionKey.of(pos)).depth(position)

    fun surfaceAt(pos: ChunkPos, tick: Long): ByteArray? =
        region(RegionKey.of(pos)).surfaceAt(RegionKey.local(pos), tick)

    /**
     * Adds a chunk version with its encoded [surface] and far-zoom [sample]; under the map's lock,
     * so the region cannot be dropped between being found and written.
     */
    fun append(pos: ChunkPos, version: LongArray, surface: ByteArray, sample: IntArray) {
        val key = RegionKey.of(pos)
        region(key)
        val added =
            synchronized(regions) { region(key).append(RegionKey.local(pos), version, surface) }
        if (added) overview.append(pos, Versions.tick(version), sample)
    }

    fun append(commit: Commit, regions: Collection<RegionKey>) = timeline.append(commit, regions)

    /**
     * Chunks in [window] (anywhere when null) whose version at [to] differs from the one at [from].
     */
    fun diff(from: Long, to: Long, window: ChunkWindow?): List<ChunkDiff.Change> {
        val changes = ArrayList<ChunkDiff.Change>()
        for (key in timeline.regionsBetween(from, to)) {
            val x0 = key.x * RegionKey.SIDE
            val z0 = key.z * RegionKey.SIDE
            if (
                window != null &&
                    (x0 + RegionKey.SIDE <= window.x0 ||
                        x0 >= window.x0 + window.width ||
                        z0 + RegionKey.SIDE <= window.z0 ||
                        z0 >= window.z0 + window.height)
            )
                continue
            val region = region(key)
            for (local in region.changedBetween(from, to)) {
                val pos = ChunkPos(x0 + (local and 31), z0 + (local shr 5))
                if (window != null && pos !in window) continue
                val before = region.at(local, from)
                val after = region.at(local, to) ?: continue
                val slots = Versions.slots(after)
                var sections = 0L
                var biomes = false
                for (slot in 0 until slots) {
                    val same =
                        before != null &&
                            Versions.slots(before) == slots &&
                            Versions.sameSlot(before, after, slot)
                    if (same) continue
                    if (slot == slots - 1) biomes = true else sections = sections or (1L shl slot)
                }
                val surface =
                    before == null ||
                        !region.surfaceAt(local, from).contentEquals(region.surfaceAt(local, to))
                changes +=
                    ChunkDiff.Change(pos, sections, biomes, surface, appeared = before == null)
            }
        }
        return changes
    }

    /** Records that segment [ordinal] named [name] is applied up to [length]. */
    fun cover(ordinal: Int, name: String, length: Long) {
        val entry = Coverage.Segment(name, length)
        if (ordinal < covered.size) covered[ordinal] = entry else covered += entry
    }

    /**
     * Saves every dirty region, the timeline and the block [kinds] the surfaces were built with,
     * then the coverage that commits them.
     */
    fun flush(kinds: ByteArray) {
        val dirty = synchronized(regions) { regions.values.filter { it.dirty } }
        for (region in dirty) files["$REGIONS/${region.key.fileName}"] = region.save()
        files += overview.flush()
        files[TIMELINE] = timeline.save()
        Files.write(directory.resolve(KINDS), kinds)
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
        private const val KINDS = "kinds"

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

        /**
         * The stored coverage if every file it vouches for is still whole and the surfaces were
         * built with the block kinds [kind] gives now (as ordinals); otherwise null, a rebuild.
         */
        fun coverage(directory: Path, kind: (Int) -> Int): Coverage? {
            val coverage = Coverage.read(directory)?.takeIf { it.intact(directory) } ?: return null
            val kinds = directory.resolve(KINDS)
            if (!Files.exists(kinds)) return null
            val stored = Files.readAllBytes(kinds)
            return coverage.takeIf { stored.indices.all { id -> stored[id].toInt() == kind(id) } }
        }
    }
}
