package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.CommitTimeline
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
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.Versions
import io.github.fopwoc.palimpsest.db.store.BlobKind
import io.github.fopwoc.palimpsest.db.store.BlobRef
import io.github.fopwoc.palimpsest.db.store.ChunkPatch
import io.github.fopwoc.palimpsest.db.store.CommitRecord
import io.github.fopwoc.palimpsest.db.store.Frames
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.Positions
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.surface.Surface
import io.github.fopwoc.palimpsest.db.surface.SurfaceScan
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A dimension that keeps every block. A commit runs in two stages: [prepare] compares and encodes
 * each chunk on the background pool, [write] lays the frame out, then indexes and publishes it on
 * the writer thread. Prepare stages run one commit at a time, in call order, so each compares
 * against the one before; a commit's prepare may overlap the previous commit's write, which is why
 * chunks prepared but not yet written are kept in [inFlight].
 */
internal class VolumeDimension(
    override val id: DimensionId,
    override val mode: DimensionMode,
    private val db: LocalDb,
    stored: List<SegmentFile>,
    private val index: DimensionIndex,
) : Dimension {
    private class Prepared(val patches: List<ChunkPatch>, val fresh: List<BlobRef>)

    @Volatile private var segments: List<SegmentFile> = stored
    private var active: SegmentFile? = null
    private val reader = BlobReader({ segments })
    private val dedup = DedupCache()
    private val inFlight = ConcurrentHashMap<ChunkPos, ChunkPatch>()

    @Volatile private var commits = CommitTimeline(index.commits.toList())

    init {
        // Neighbours share content (solid stone, open sky), so a region's chunks seed
        // deduplication.
        index.onLoadForWrite = { region ->
            region.latest().forEach { version ->
                Versions.refs(version).forEach { it?.let(dedup::remember) }
            }
        }
    }

    private val order = Any()
    private var lastTick: WorldTick? = commits.lastOrNull()?.tick
    private var prepareTail: CompletableFuture<*> = CompletableFuture.completedFuture(null)
    private var writeTail: CompletableFuture<*> = CompletableFuture.completedFuture(null)

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
            lastTick?.let {
                if (tick <= it) return CompletableFuture.failedFuture(TickOrderException(tick, it))
            }
            lastTick = tick
            val prepared = prepareTail.thenCompose { prepare(unique) }
            // The next prepare starts only after a failure here is recorded, never on top of it.
            prepareTail = prepared.handle { _, error -> if (error != null) fail(error) }
            val written = prepared.thenApplyAsync({ write(tick, it) }, db.threads.writer)
            written.whenComplete { _, error -> if (error != null) fail(error) }
            writeTail = written
            return written
        }
    }

    override val latest: Commit?
        get() = commits.lastOrNull()

    override fun timeline(): CommitTimeline = commits

    override fun at(tick: WorldTick): Snapshot = VolumeSnapshot(commits.atOrBefore(tick))

    /** Waits for every commit accepted so far. */
    fun drain() {
        synchronized(order) { writeTail }.handle { _, _ -> }.join()
    }

    /** The manifest's view of this dimension; writer thread only. */
    fun entry(sealActive: Boolean): Manifest.DimensionEntry =
        Manifest.DimensionEntry(
            id,
            mode,
            segments.map {
                Manifest.SegmentEntry(it.name, it.length, sealed = it !== active || sealActive)
            },
        )

    /** Writer thread only: truth first, the index after the manifest has committed it. */
    fun force() = active?.force()

    fun flushIndex() = index.flush(db.kinds.snapshot(db.vocabulary.size))

    fun closeFiles() = segments.forEach(SegmentFile::close)

    private fun prepare(observations: List<ChunkObservation>): CompletableFuture<Prepared> {
        failure?.let {
            return CompletableFuture.failedFuture(it)
        }
        val fresh = ConcurrentLinkedQueue<BlobRef>()
        val tasks = observations.map {
            CompletableFuture.supplyAsync({ prepare(it, fresh) }, db.threads.background)
        }
        return CompletableFuture.allOf(*tasks.toTypedArray()).thenApply {
            Prepared(tasks.mapNotNull { it.join() }, fresh.toList())
        }
    }

    /** The chunk's patch against its previous version, or null when nothing changed. */
    private fun prepare(
        observation: ChunkObservation,
        fresh: MutableCollection<BlobRef>,
    ): ChunkPatch? {
        val slotCount = observation.sections.size + 1
        val previous =
            (inFlight[observation.pos]?.let { it.minSection to it.slots }
                    ?: index.latest(observation.pos)?.let {
                        Versions.minSection(it) to Versions.refs(it)
                    })
                ?.takeIf { (minSection, slots) ->
                    minSection == observation.minSection && slots.size == slotCount
                }
                ?.second
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
            slots[slot] = values?.let { obtain(hash!!, kind, it, fresh) }
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
        val surface = SurfaceScan.scan(contents, observation.minSection, biomes, db.kinds::kind)
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

    /** A stored blob for [hash] if one is remembered, otherwise a freshly encoded one. */
    private fun obtain(
        hash: ContentHash,
        kind: BlobKind,
        values: IntArray,
        fresh: MutableCollection<BlobRef>,
    ): BlobRef {
        dedup.get(hash)?.let {
            return it
        }
        val sink = ByteSink(if (kind == BlobKind.SECTION) 512 else 64)
        when (kind) {
            BlobKind.SECTION -> SectionCodec.encode(values, sink)
            BlobKind.BIOMES -> BiomeCodec.encode(values, sink)
        }
        val blob = BlobRef.fresh(hash, kind, sink.toByteArray())
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
                prepared.fresh.count { it.kind == BlobKind.SECTION },
                Frames.HEADER + payload.size.toLong() + vocabularyBytes,
            )
        index.append(commit)
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
        if (failure != null) return
        failure = error
        db.log.log(
            LogLevel.ERROR,
            "writer",
            "Commit to $id failed; the dimension stops accepting commits",
            error,
        )
    }

    private inner class VolumeSnapshot(override val commit: Commit?) : Snapshot {
        override fun overview(window: ChunkWindow, level: Int): Request<SampleGrid> =
            FutureRequest(db.threads.interactive) {
                index.overview.grid(window, level, commit?.tick?.value ?: Long.MIN_VALUE)
            }

        /** One task per row of chunks, so a window decodes on every read thread at once. */
        override fun surface(window: ChunkWindow): Request<SurfaceGrid> {
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

        override fun volume(chunk: ChunkPos): Request<ChunkVolume?> =
            FutureRequest(db.threads.interactive) {
                val version =
                    commit?.let { index.at(chunk, it.tick.value) } ?: return@FutureRequest null
                val slots = Versions.slots(version)
                fun decode(slot: Int): IntArray? {
                    val position = Versions.position(version, slot)
                    if (position == Positions.AIR) return null
                    return reader.decode(
                        position,
                        Versions.length(version, slot),
                        BlobKind.of(slot, slots),
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
}
