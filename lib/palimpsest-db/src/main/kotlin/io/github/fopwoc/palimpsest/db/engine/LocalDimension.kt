package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Activity
import io.github.fopwoc.palimpsest.db.ChunkDiff
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.CommitTimeline
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.Request
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.SampleGrid
import io.github.fopwoc.palimpsest.db.Snapshot
import io.github.fopwoc.palimpsest.db.SurfaceGrid
import io.github.fopwoc.palimpsest.db.TickOrderException
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.codec.ByteSink
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One dimension's history. It loads in the background ([ready]): compaction, then the index caught
 * up or rebuilt. A commit runs in two stages: [prepare] compares and encodes each chunk on the
 * background pool, [write] lays the frame out, then indexes and publishes it on the writer thread.
 * Prepare stages run one commit at a time, in call order and after loading, so each compares
 * against the one before; a commit's prepare may overlap the previous commit's write, which is why
 * chunks prepared but not yet written stay in flight in [ChunkPreparer].
 */
internal class LocalDimension(
    override val id: DimensionId,
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
    private lateinit var reads: MomentReads
    private lateinit var preparer: ChunkPreparer

    @Volatile private var segments: List<SegmentFile> = emptyList()
    private var active: SegmentFile? = null
    private val reader = BlobReader({ segments })

    @Volatile private var commits = CommitTimeline(emptyList())

    /**
     * The last tick accepted into history; only the commit chain touches it, one stage at a time.
     */
    private var lastTick: WorldTick? = null

    override val retention: Retention
        get() = db.retention(id)

    private val loading = CompletableFuture.supplyAsync(load, db.threads.background)

    override val ready: CompletableFuture<Unit> = loading.thenApply { install(it) }

    init {
        ready.whenComplete { _, error ->
            if (error != null) {
                db.log.log(
                    LogLevel.ERROR,
                    "open",
                    "Loading $id failed; it takes no commits or reads",
                    error,
                )
                return@whenComplete
            }
            // Only once ready, so the flush that drops them from the manifest names the compacted
            // segment instead: before it, a flush would still list the files about to go.
            val superseded = loading.join().superseded
            if (superseded.isNotEmpty()) db.retire(superseded)
        }
    }

    private val order = Any()
    private var prepareTail: CompletableFuture<*> = ready
    private var writeTail: CompletableFuture<*> = ready

    /** Set by the first failed commit; a dimension's later commits would build on missing data. */
    @Volatile private var failure: Throwable? = null

    override fun commit(
        tick: WorldTick,
        observations: Collection<ChunkObservation>,
    ): CompletableFuture<Commit> =
        enqueue(observations.associateBy { it.pos }.values.toList()) { last ->
            last?.let { if (tick <= it) throw TickOrderException(tick, it) }
            tick
        }

    /**
     * Chains a commit of [unique] observations whose tick [resolve] picks from the last one;
     * resolved in the chain, since before loading finishes the last tick of history is unknown.
     */
    private fun enqueue(
        unique: List<ChunkObservation>,
        resolve: (WorldTick?) -> WorldTick,
    ): CompletableFuture<Commit> {
        synchronized(order) {
            failure?.let {
                return CompletableFuture.failedFuture(
                    IllegalStateException("Dimension $id failed", it)
                )
            }
            val prepared = prepareTail.thenCompose {
                val tick = resolve(lastTick)
                lastTick = tick
                prepare(unique).thenApply { tick to it }
            }
            // The next prepare starts only after a failure here is recorded, never on top of it.
            prepareTail = prepared.handle { _, error -> error?.let(::fail) }
            // After the previous write too: the next commit's prepare may finish before this one's
            // write is even queued, and history must be written in tick order.
            val written =
                prepared.thenCombineAsync(
                    writeTail.handle { _, _ -> },
                    { (tick, batch), _ -> write(tick, batch) },
                    db.threads.writer,
                )
            written.whenComplete { _, error -> error?.let(::fail) }
            writeTail = written
            return written
        }
    }

    private val staged = ConcurrentHashMap<ChunkPos, ChunkObservation>()

    override fun stage(observation: ChunkObservation) {
        staged[observation.pos] = observation
    }

    override val stagedCount: Int
        get() = staged.size

    override fun commitStaged(tick: WorldTick): CompletableFuture<Commit> {
        // Taken one by one, so a chunk staged meanwhile waits for the next commit instead of being
        // lost.
        val batch = staged.keys.toList().mapNotNull { staged.remove(it) }
        if (batch.isEmpty())
            return CompletableFuture.completedFuture(
                Commit(tick, System.currentTimeMillis(), 0, 0, 0)
            )
        return enqueue(batch) { last -> last?.let { maxOf(tick, WorldTick(it.value + 1)) } ?: tick }
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
                retention,
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

    /** Names of every segment file the dimension has, for deleting it. */
    fun segmentNames(): List<String> = segments.map { it.name }

    /** Dropped from the world: later commits fail; call after [drain]. */
    fun retire() {
        failure = IllegalStateException("Dimension $id was dropped")
    }

    private val loaded: Boolean
        get() = ready.isDone && !ready.isCompletedExceptionally

    /**
     * Takes over what loading produced; the volatile [commits] write publishes [index] to readers.
     */
    private fun install(loaded: Loaded) {
        index = loaded.index
        segments = loaded.segments
        reads = MomentReads(db, index, reader)
        preparer = ChunkPreparer(index, reader, db.kinds::kind)
        index.onLoadForWrite = preparer::seed
        lastTick = index.commits.lastOrNull()?.tick
        commits = CommitTimeline(index.commits.toList())
    }

    private fun prepare(observations: List<ChunkObservation>): CompletableFuture<Prepared> {
        failure?.let {
            return CompletableFuture.failedFuture(it)
        }
        val fresh = ConcurrentLinkedQueue<BlobRef>()
        val progress = db.activities.start(Activity.Task.COMMITTING, id, observations.size.toLong())
        val tasks = observations.map {
            CompletableFuture.supplyAsync(
                { preparer.prepare(it, fresh).also { progress.advance(1) } },
                db.threads.background,
            )
        }
        return CompletableFuture.allOf(*tasks.toTypedArray())
            .whenComplete { _, _ -> progress.close() }
            .thenApply { Prepared(tasks.mapNotNull { it.join() }, fresh.toList()) }
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
            preparer.written(patch)
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

        private fun <T> gated(read: (Long?) -> Request<T>): Request<T> =
            if (resolved.isDone) read(commit?.tick?.value)
            else GatedRequest(resolved) { read(commit?.tick?.value) }

        override fun surface(window: ChunkWindow): Request<SurfaceGrid> = gated {
            reads.surface(window, it)
        }

        override fun ceiling(window: ChunkWindow, y: Int): Request<SurfaceGrid> = gated {
            reads.ceiling(window, y, it)
        }

        override fun overview(window: ChunkWindow, level: Int): Request<SampleGrid> = gated {
            reads.overview(window, level, it)
        }

        override fun volume(chunk: ChunkPos): Request<ChunkVolume?> = gated {
            reads.volume(chunk, it)
        }
    }
}
