package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Activity
import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.ChunkDiff
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.CommitTimeline
import io.github.fopwoc.palimpsest.db.Depth
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.Request
import io.github.fopwoc.palimpsest.db.SampleGrid
import io.github.fopwoc.palimpsest.db.Snapshot
import io.github.fopwoc.palimpsest.db.SurfaceGrid
import io.github.fopwoc.palimpsest.db.TickOrderException
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.BiomeCodec
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.codec.ContentHash
import io.github.fopwoc.palimpsest.db.codec.SectionCodec
import io.github.fopwoc.palimpsest.db.codec.SectionDelta
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.OverviewIndex
import io.github.fopwoc.palimpsest.db.index.RegionKey
import io.github.fopwoc.palimpsest.db.index.Versions
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobReader
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.ChunkPatch
import io.github.fopwoc.palimpsest.db.store.CommitRecord
import io.github.fopwoc.palimpsest.db.store.Frames
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.surface.Sections
import io.github.fopwoc.palimpsest.db.surface.Surface
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One dimension's history. It loads in the background ([ready]): compaction, then the index caught
 * up or rebuilt. A commit runs in two stages: [prepare] compares and encodes each chunk on the
 * background pool, [write] lays the frame out, then indexes and publishes it on the writer thread.
 * Prepare stages run one commit at a time, in call order and after loading, so each compares
 * against the one before; a commit's prepare may overlap the previous commit's write, which is why
 * chunks prepared but not yet written are kept in [inFlight].
 */
internal class LocalDimension(
    override val id: DimensionId,
    override val mode: DimensionMode,
    private val db: LocalDb,
    load: () -> Loaded,
) : Dimension {
    /** What loading hands over; [superseded] are files compaction replaced. */
    class Loaded(
        val segments: List<SegmentFile>,
        val index: DimensionIndex,
        val superseded: List<java.nio.file.Path>,
    )

    private class Prepared(val patches: List<ChunkPatch>, val fresh: List<BlobRef>)

    /** Set once by loading; read only after [commits] or [ready] show it is there. */
    private lateinit var index: DimensionIndex

    @Volatile private var segments: List<SegmentFile> = emptyList()
    private var active: SegmentFile? = null
    private val reader = BlobReader({ segments })
    private val dedup = DedupCache()
    private val inFlight = ConcurrentHashMap<ChunkPos, ChunkPatch>()

    @Volatile private var commits = CommitTimeline(emptyList())

    /**
     * The last tick accepted into history; only the commit chain touches it, one stage at a time.
     */
    private var lastTick: WorldTick? = null

    override val ready: CompletableFuture<Unit> =
        CompletableFuture.supplyAsync(load, db.threads.background).thenApply { install(it) }

    private val order = Any()
    private var prepareTail: CompletableFuture<*> = ready
    private var writeTail: CompletableFuture<*> = ready

    /** Set by the first failed commit; a dimension's later commits would build on missing data. */
    @Volatile private var failure: Throwable? = null

    override fun commit(
        tick: WorldTick,
        observations: Collection<ChunkObservation>,
    ): CompletableFuture<Commit> {
        val unique = observations.associateBy { it.pos }.values.toList()
        synchronized(order) {
            failure?.let {
                return CompletableFuture.failedFuture(
                    IllegalStateException("Dimension $id failed", it)
                )
            }
            // Checked in the chain: before loading finishes the last tick of history is unknown.
            val prepared = prepareTail.thenCompose {
                lastTick?.let { last -> if (tick <= last) throw TickOrderException(tick, last) }
                lastTick = tick
                prepare(unique)
            }
            // The next prepare starts only after a failure here is recorded, never on top of it.
            prepareTail = prepared.handle { _, error -> error?.let(::fail) }
            val written = prepared.thenApplyAsync({ write(tick, it) }, db.threads.writer)
            written.whenComplete { _, error -> error?.let(::fail) }
            writeTail = written
            return written
        }
    }

    override val latest: Commit?
        get() = commits.lastOrNull()

    override fun timeline(): CommitTimeline = commits

    override fun at(tick: WorldTick): Snapshot = VolumeSnapshot(tick)

    override fun diff(from: WorldTick, to: WorldTick, window: ChunkWindow?): Request<ChunkDiff> {
        require(from <= to) { "A diff runs forward: ${from.value} > ${to.value}" }
        return if (ready.isDone) diffNow(from, to, window)
        else GatedRequest(ready) { diffNow(from, to, window) }
    }

    private fun diffNow(from: WorldTick, to: WorldTick, window: ChunkWindow?): Request<ChunkDiff> {
        // Moments past the last published commit read as that commit, like snapshots do.
        val last = commits.lastOrNull()?.tick ?: from
        val end = minOf(to, last)
        return FutureRequest(db.threads.interactive) {
            ChunkDiff(
                from,
                to,
                if (end <= from) emptyList() else index.diff(from.value, end.value, window),
            )
        }
    }

    /** Waits for every commit accepted so far. */
    fun drain() {
        synchronized(order) { writeTail }.handle { _, _ -> }.join()
    }

    /** The manifest's view of this dimension, or null while it loads; writer thread only. */
    fun entry(sealActive: Boolean): Manifest.DimensionEntry? =
        if (!loaded) null
        else
            Manifest.DimensionEntry(
                id,
                mode,
                segments.map {
                    Manifest.FileEntry(it.name, it.length, sealed = it !== active || sealActive)
                },
            )

    /** Writer thread only: truth first, the index after the manifest has committed it. */
    fun force() = active?.force()

    fun flushIndex() {
        if (loaded) index.flush(db.kinds.snapshot(db.vocabulary.size))
    }

    fun closeFiles() = segments.forEach(SegmentFile::close)

    private val loaded: Boolean
        get() = ready.isDone && !ready.isCompletedExceptionally

    /**
     * Takes over what loading produced; the volatile [commits] write publishes [index] to readers.
     */
    private fun install(loaded: Loaded) {
        index = loaded.index
        segments = loaded.segments
        // Neighbours share content (solid stone, open sky), so a region's chunks seed
        // deduplication.
        index.onLoadForWrite = { region ->
            region.latest().forEach { version ->
                Versions.refs(version).forEach { blob ->
                    blob?.takeUnless { it.delta }?.let(dedup::remember)
                }
            }
        }
        lastTick = index.commits.lastOrNull()?.tick
        commits = CommitTimeline(index.commits.toList())
        if (loaded.superseded.isNotEmpty()) db.retire(loaded.superseded)
    }

    private fun prepare(observations: List<ChunkObservation>): CompletableFuture<Prepared> {
        failure?.let {
            return CompletableFuture.failedFuture(it)
        }
        val fresh = ConcurrentLinkedQueue<BlobRef>()
        val progress = db.activities.start(Activity.Task.COMMITTING, id, observations.size.toLong())
        val tasks = observations.map {
            CompletableFuture.supplyAsync(
                { prepare(it, fresh).also { progress.advance(1) } },
                db.threads.background,
            )
        }
        return CompletableFuture.allOf(*tasks.toTypedArray())
            .whenComplete { _, _ -> progress.close() }
            .thenApply { Prepared(tasks.mapNotNull { it.join() }, fresh.toList()) }
    }

    /** The previous version's slots, if its shape matches [slotCount] slots from [minSection]. */
    private fun previous(pos: ChunkPos, minSection: Int, slotCount: Int): Array<BlobRef?>? =
        (inFlight[pos]?.let { it.minSection to it.slots }
                ?: index.latest(pos)?.let { Versions.minSection(it) to Versions.refs(it) })
            ?.takeIf { (min, slots) -> min == minSection && slots.size == slotCount }
            ?.second

    /** The chunk's patch against its previous version, or null when nothing changed. */
    private fun prepare(
        observation: ChunkObservation,
        fresh: MutableCollection<BlobRef>,
    ): ChunkPatch? =
        when (mode.depth) {
            Depth.VOLUME -> prepareVolume(observation, fresh)
            Depth.SURFACE -> prepareSurface(observation, fresh)
        }

    /** Surface-only: the chunk's summary is its truth, stored when it changes, and nothing else. */
    private fun prepareSurface(
        observation: ChunkObservation,
        fresh: MutableCollection<BlobRef>,
    ): ChunkPatch? {
        val sections =
            Array(observation.sections.size) {
                observation.sections[it]?.unpack()?.takeUnless { blocks ->
                    blocks.all { id -> id == 0 }
                }
            }
        val biomes =
            when (val biomes = observation.biomes) {
                is Biomes.Columns -> biomes.values
            }
        val surface =
            SurfaceScan.scan(Sections.of(sections), observation.minSection, biomes, db.kinds::kind)
        val encoded = surface.encode(observation.minSection * 16)
        val hash = ContentHash.of(encoded, BlobKind.SURFACE.ordinal)
        val previous = previous(observation.pos, observation.minSection, 1)
        if (previous?.get(0)?.hash == hash) return null
        val slots = arrayOf<BlobRef?>(obtain(hash, BlobKind.SURFACE, fresh) { encoded })
        return ChunkPatch(
                observation.pos,
                observation.minSection,
                1L,
                slots,
                encoded,
                surface.sample(),
            )
            .also { inFlight[observation.pos] = it }
    }

    private fun prepareVolume(
        observation: ChunkObservation,
        fresh: MutableCollection<BlobRef>,
    ): ChunkPatch? {
        val slotCount = observation.sections.size + 1
        val previous = previous(observation.pos, observation.minSection, slotCount)
        val slots = arrayOfNulls<BlobRef>(slotCount)
        val contents = arrayOfNulls<IntArray>(slotCount - 1)
        var mask = 0L
        fun place(slot: Int, values: IntArray?, kind: BlobKind) {
            if (kind == BlobKind.SECTION) contents[slot] = values
            val hash = values?.let { ContentHash.of(it, kind.ordinal) }
            val before = previous?.get(slot)
            if (previous != null && hash == before?.hash) {
                slots[slot] = before
                return
            }
            mask = mask or (1L shl slot)
            slots[slot] = values?.let {
                val delta =
                    if (kind == BlobKind.SECTION) delta(observation.pos, before, hash!!, it)
                    else null
                delta?.also(fresh::add)
                    ?: obtain(hash!!, kind, fresh) {
                        val sink = ByteSink(if (kind == BlobKind.SECTION) 512 else 64)
                        if (kind == BlobKind.SECTION) SectionCodec.encode(it, sink)
                        else BiomeCodec.encode(it, sink)
                        sink.toByteArray()
                    }
            }
        }
        observation.sections.forEachIndexed { slot, section ->
            place(
                slot,
                section?.unpack()?.takeUnless { blocks -> blocks.all { it == 0 } },
                BlobKind.SECTION,
            )
        }
        val biomes =
            when (val biomes = observation.biomes) {
                is Biomes.Columns -> biomes.values
            }
        place(observation.sections.size, biomes, BlobKind.BIOMES)
        if (mask == 0L) return null
        val surface =
            SurfaceScan.scan(Sections.of(contents), observation.minSection, biomes, db.kinds::kind)
        val encoded = surface.encode(observation.minSection * 16)
        return ChunkPatch(
                observation.pos,
                observation.minSection,
                mask,
                slots,
                encoded,
                surface.sample(),
            )
            .also {
                inFlight[observation.pos] = it
            }
    }

    /**
     * The section as a delta against [before], the same slot's previous version, when that is worth
     * it: [before] is stored, its chain is short, and no full blob with this content is remembered.
     * Deltas never enter deduplication; their base is this chunk's own history.
     */
    private fun delta(
        pos: ChunkPos,
        before: BlobRef?,
        hash: ContentHash,
        after: IntArray,
    ): BlobRef? {
        if (before == null || !before.positioned || dedup.get(hash) != null) return null
        val bases = index.bases(pos)
        val depth = if (before.delta) index.depth(pos, before.position) else 0
        if (depth >= MAX_CHAIN) return null
        val previous = reader.decode(before.position, before.length, BlobKind.SECTION, bases)
        val bytes = SectionDelta.encode(previous, after) ?: return null
        return BlobRef.delta(hash, bytes, before, depth)
    }

    /** A stored blob for [hash] if one is remembered, otherwise a fresh one from [encode]. */
    private fun obtain(
        hash: ContentHash,
        kind: BlobKind,
        fresh: MutableCollection<BlobRef>,
        encode: () -> ByteArray,
    ): BlobRef {
        dedup.get(hash)?.let {
            return it
        }
        val blob = BlobRef.fresh(hash, kind, encode())
        return dedup.remember(blob).also { if (it === blob) fresh += blob }
    }

    private fun write(tick: WorldTick, prepared: Prepared): Commit {
        failure?.let { throw IllegalStateException("Dimension $id failed", it) }
        val observedAt = System.currentTimeMillis()
        if (prepared.patches.isEmpty()) return Commit(tick, observedAt, 0, 0, 0)
        val vocabularyBytes = db.vocabulary.persist()
        val segment = active ?: openSegment()
        val sink = ByteSink(prepared.fresh.sumOf { it.length } + prepared.patches.size * 32 + 64)
        sink.varint(prepared.fresh.sumOf { it.length.toLong() })
        val blobsStart = segment.length + Frames.HEADER + sink.size
        prepared.fresh.forEach { sink.bytes(it.pending!!) }
        CommitRecord.encode(sink, tick.value, observedAt, prepared.fresh, prepared.patches)
        val payload = sink.toByteArray()
        segment.append(payload)
        var offset = blobsStart
        for (blob in prepared.fresh) {
            blob.position = Positions.of(segment.ordinal, offset)
            blob.pending = null
            offset += blob.length
        }
        for (patch in prepared.patches) {
            index.append(
                patch.pos,
                Versions.of(tick.value, patch.minSection, patch.slots),
                patch.surface,
                patch.sample,
            )
            inFlight.remove(patch.pos, patch)
        }
        index.cover(segment.ordinal, segment.name, segment.length)
        val commit =
            Commit(
                tick,
                observedAt,
                prepared.patches.size,
                prepared.fresh.count { it.kind != BlobKind.BIOMES },
                Frames.HEADER + payload.size.toLong() + vocabularyBytes,
            )
        index.append(commit, prepared.patches.map { RegionKey.of(it.pos) }.toSet())
        commits = CommitTimeline(commits + commit)
        db.log.log(
            LogLevel.DEBUG,
            "writer",
            "$id: ${commit.chunksChanged} chunks, ${commit.bytes} bytes at tick ${tick.value}",
            null,
        )
        db.afterWrite()
        return commit
    }

    private fun openSegment(): SegmentFile {
        val name = "${db.session}.seg"
        val segment =
            SegmentFile.create(
                db.layout.segment(id, name),
                segments.size,
                PalimpsestDb.GENERATION,
                id,
                db.session,
            )
        segments = segments + segment
        active = segment
        return segment
    }

    private fun fail(error: Throwable) {
        // A commit out of order is dropped alone; everything else stops the dimension.
        if ((error.cause ?: error) is TickOrderException) return
        if (failure != null) return
        failure = error
        db.log.log(
            LogLevel.ERROR,
            "writer",
            "Commit to $id failed; the dimension stops accepting commits",
            error,
        )
    }

    /**
     * The world as of [tick]: resolved to a commit now if the dimension is loaded, otherwise once
     * it is, and every read asked for meanwhile waits for that.
     */
    private inner class VolumeSnapshot(tick: WorldTick) : Snapshot {
        private val resolved: CompletableFuture<Commit?> =
            if (ready.isDone) CompletableFuture.completedFuture(commits.atOrBefore(tick))
            else ready.thenApply { commits.atOrBefore(tick) }

        override val commit: Commit?
            get() = resolved.getNow(null)

        private fun <T> gated(read: () -> Request<T>): Request<T> =
            if (resolved.isDone) read() else GatedRequest(resolved, read)

        override fun surface(window: ChunkWindow): Request<SurfaceGrid> = gated {
            surfaceNow(window)
        }

        override fun ceiling(window: ChunkWindow, y: Int): Request<SurfaceGrid> = gated {
            ceilingNow(window, y)
        }

        override fun overview(window: ChunkWindow, level: Int): Request<SampleGrid> = gated {
            overviewNow(window, level)
        }

        override fun volume(chunk: ChunkPos): Request<ChunkVolume?> = gated { volumeNow(chunk) }

        private fun overviewNow(window: ChunkWindow, level: Int): Request<SampleGrid> =
            FutureRequest(db.threads.interactive) {
                val tick = commit?.tick?.value
                if (tick == null) OverviewIndex.empty(window, level)
                else index.overview.grid(window, level, tick)
            }

        /** One task per row of chunks, so a window decodes on every read thread at once. */
        private fun surfaceNow(window: ChunkWindow): Request<SurfaceGrid> {
            val grid = SurfaceGrid.builder(window)
            val tick = commit?.tick?.value
            val rows =
                if (tick == null) emptyList()
                else
                    (window.z0 until window.z0 + window.height).map { z ->
                        {
                            for (x in window.x0 until window.x0 + window.width) {
                                val pos = ChunkPos(x, z)
                                val bytes = index.surfaceAt(pos, tick) ?: continue
                                val surface = Surface.decode(bytes)
                                grid.put(
                                    pos,
                                    surface.block,
                                    surface.height,
                                    surface.depth,
                                    surface.biome,
                                )
                            }
                        }
                    }
            return SplitRequest(db.threads.interactive, rows, grid::build)
        }

        /**
         * The cold path: each chunk's sections decoded from history as its columns reach them,
         * scanned down from [y]. Near a cave ceiling that is usually one or two sections.
         */
        private fun ceilingNow(window: ChunkWindow, y: Int): Request<SurfaceGrid> {
            // Nothing under the roof is stored in a surface-only dimension.
            if (mode.depth == Depth.SURFACE) return surfaceNow(window)
            val grid = SurfaceGrid.builder(window)
            val tick = commit?.tick?.value
            val rows =
                if (tick == null) emptyList()
                else
                    (window.z0 until window.z0 + window.height).map { z ->
                        {
                            for (x in window.x0 until window.x0 + window.width) {
                                val pos = ChunkPos(x, z)
                                val version = index.at(pos, tick) ?: continue
                                val slots = Versions.slots(version)
                                fun load(slot: Int) =
                                    reader.decode(
                                        Versions.position(version, slot),
                                        Versions.length(version, slot),
                                        BlobKind.of(slot, slots),
                                        index.bases(pos),
                                    )
                                val sections =
                                    Sections(
                                        slots - 1,
                                        { Versions.position(version, it) != Positions.AIR },
                                        ::load,
                                    )
                                val surface =
                                    SurfaceScan.scan(
                                        sections,
                                        Versions.minSection(version),
                                        load(slots - 1),
                                        db.kinds::kind,
                                        y,
                                    )
                                grid.put(
                                    pos,
                                    surface.block,
                                    surface.height,
                                    surface.depth,
                                    surface.biome,
                                )
                            }
                        }
                    }
            return SplitRequest(db.threads.interactive, rows, grid::build)
        }

        private fun volumeNow(chunk: ChunkPos): Request<ChunkVolume?> =
            FutureRequest(db.threads.interactive) {
                val pos = chunk
                val version =
                    commit?.let { index.at(chunk, it.tick.value) } ?: return@FutureRequest null
                val slots = Versions.slots(version)
                if (mode.depth == Depth.SURFACE) return@FutureRequest null
                fun decode(slot: Int): IntArray? {
                    val position = Versions.position(version, slot)
                    if (position == Positions.AIR) return null
                    return reader.decode(
                        position,
                        Versions.length(version, slot),
                        BlobKind.of(slot, slots),
                        index.bases(pos),
                    )
                }
                val sections = Array(slots - 1, ::decode)
                DecodedVolume(
                    chunk,
                    Versions.minSection(version),
                    sections,
                    Biomes.Columns(decode(slots - 1)!!),
                )
            }
    }

    private companion object {
        /**
         * Deltas in a row before a section is stored whole again: a read decodes at most this many.
         */
        const val MAX_CHAIN = 8
    }
}
