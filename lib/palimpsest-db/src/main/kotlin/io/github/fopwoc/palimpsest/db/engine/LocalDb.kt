package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.DbLog
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.IndexReplay
import io.github.fopwoc.palimpsest.db.store.BranchArchive
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.ManifestGraph
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.store.VocabularyStore
import io.github.fopwoc.palimpsest.db.store.WorldLayout
import io.github.fopwoc.palimpsest.db.store.WorldLock
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** A world folder opened for this session: the one writer of its history until [close]. */
internal class LocalDb
private constructor(
    val layout: WorldLayout,
    config: DbConfig,
    private val lock: WorldLock,
    private val previous: Manifest?,
    val session: Manifest.Session,
    override val vocabulary: VocabularyStore,
) : PalimpsestDb {
    val log: DbLog = config.log
    private val cacheDirectory = config.cacheDirectory
    val threads = DbThreads(config)
    val kinds = KindTable(vocabulary, config.blockKinds)
    private val dimensions = ConcurrentHashMap<DimensionId, LocalDimension>()
    private var lastFlush = System.nanoTime()

    @Volatile private var closed = false

    override fun dimension(id: DimensionId, mode: DimensionMode): Dimension {
        check(!closed) { "Database is closed" }
        return dimensions[id]
            ?: synchronized(dimensions) { dimensions.getOrPut(id) { load(id, mode) } }
    }

    override fun flush() {
        check(!closed) { "Database is closed" }
        dimensions.values.forEach(LocalDimension::drain)
        threads.writer.submit { flushNow(seal = false) }.get()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            dimensions.values.forEach(LocalDimension::drain)
            threads.writer.submit { flushNow(seal = true) }.get()
            log.log(LogLevel.INFO, "open", "Closed $session", null)
        } finally {
            threads.close()
            dimensions.values.forEach(LocalDimension::closeFiles)
            vocabulary.close()
            lock.close()
        }
    }

    /**
     * Called by the writer after every commit frame: makes it durable at most every [FLUSH_EVERY].
     */
    fun afterWrite() {
        if (System.nanoTime() - lastFlush >= FLUSH_EVERY) flushNow(seal = false)
    }

    /** Writer thread only: data first, then this session's manifest that commits it. */
    private fun flushNow(seal: Boolean) {
        vocabulary.force()
        dimensions.values.forEach(LocalDimension::force)
        val touched = dimensions.values.associate { it.id to it.entry(seal) }
        val untouched = previous?.dimensions.orEmpty().filter { it.id !in touched }
        Manifest(
                generation = PalimpsestDb.GENERATION,
                session = session,
                parent = previous?.session,
                writtenAt = System.currentTimeMillis(),
                vocabulary = vocabulary.entries(seal),
                dimensions = untouched + touched.values,
            )
            .write(layout)
        // This session is the head now; its parent's manifest only names files ours names too.
        previous?.let { Files.deleteIfExists(layout.manifest(it.session)) }
        dimensions.values.forEach(LocalDimension::flushIndex)
        lastFlush = System.nanoTime()
    }

    private fun load(id: DimensionId, mode: DimensionMode): LocalDimension {
        val entry = previous?.dimensions?.firstOrNull { it.id == id }
        if (entry != null)
            require(entry.mode == mode) {
                "$id is stored as ${entry.mode}; changing modes is not supported yet"
            }
        val started = System.nanoTime()
        val segments =
            entry?.segments.orEmpty().mapIndexed { ordinal, stored ->
                SegmentFile.open(layout.segment(id, stored.name), ordinal, stored.length)
            }
        val directory = cacheDirectory.resolve(id.key)
        val coverage =
            DimensionIndex.coverage(directory) { id ->
                // Ids the vocabulary no longer has: the index saw history a crash took back.
                if (id < vocabulary.size) kinds.kind(id).ordinal else -1
            }
        val fits =
            coverage != null &&
                coverage.segments.size <= segments.size &&
                coverage.segments.withIndex().all { (ordinal, covered) ->
                    covered.name == segments[ordinal].name &&
                        covered.length <= segments[ordinal].length
                }
        val index = DimensionIndex.open(directory, REGIONS, fresh = !fits)
        val frames = IndexReplay(index, segments, kinds::kind).run()
        if (frames > 0) index.flush(kinds.snapshot(vocabulary.size))
        log.log(
            LogLevel.INFO,
            "open",
            "$id: ${index.commits.size} commits, ${if (fits) "index caught up" else "index rebuilt"} " +
                "from $frames frames in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms",
            null,
        )
        return LocalDimension(id, mode, this, segments, index)
    }

    companion object {
        private val FLUSH_EVERY = TimeUnit.SECONDS.toNanos(30)

        /** Index regions kept in memory per dimension: an 8×8-region area, far beyond any view. */
        private const val REGIONS = 64

        fun open(world: Path, config: DbConfig): OpenResult {
            Files.createDirectories(world)
            val layout = WorldLayout(world)
            val lock =
                WorldLock.tryAcquire(layout.lock)
                    ?: return OpenResult.Locked(WorldLock.holder(layout.lock))
            var opened = false
            try {
                val head =
                    when (val state = state(layout)) {
                        is State.Blocked -> return state.result
                        is State.Ready -> state.head
                    }
                val session =
                    Manifest.Session(
                        (head?.session?.number ?: 0) + 1,
                        "%08x".format(SecureRandom().nextInt()),
                    )
                val vocabulary = VocabularyStore.open(layout, head?.vocabulary.orEmpty(), session)
                config.log.log(
                    LogLevel.INFO,
                    "open",
                    "Opened $world as session $session after ${head?.session}",
                    null,
                )
                return OpenResult.Opened(LocalDb(layout, config, lock, head, session, vocabulary))
                    .also { opened = true }
            } finally {
                if (!opened) lock.close()
            }
        }

        fun keep(world: Path, branch: OpenResult.Diverged.Branch): Boolean {
            val layout = WorldLayout(world)
            val lock = WorldLock.tryAcquire(layout.lock) ?: return false
            lock.use {
                val graph = ManifestGraph.read(layout)
                val kept = graph.heads.firstOrNull { it.session.toString() == branch.session }
                requireNotNull(kept) { "No branch ${branch.session} in $world" }
                BranchArchive.keep(layout, graph, kept)
            }
            return true
        }

        private sealed interface State {
            class Ready(val head: Manifest?) : State

            class Blocked(val result: OpenResult) : State
        }

        /** The manifest to continue from, or why the folder cannot be opened as it is. */
        private fun state(layout: WorldLayout): State {
            val graph = ManifestGraph.read(layout)
            if (graph.unreadable.isNotEmpty())
                return State.Blocked(
                    OpenResult.SyncIncomplete(graph.unreadable.map(layout::relative))
                )
            if (graph.manifests.isEmpty()) {
                val others =
                    layout.root.listDirectoryEntries().filter { it.name != layout.lock.name }
                return when {
                    others.isEmpty() -> State.Ready(null)
                    // History without a manifest: it has not arrived yet.
                    layout.vocabulary.exists() || layout.dimensions.exists() ->
                        State.Blocked(
                            OpenResult.SyncIncomplete(listOf(layout.relative(layout.manifests)))
                        )
                    else -> State.Blocked(OpenResult.Incompatible(generation = 0))
                }
            }
            graph.manifests
                .firstOrNull { it.generation != PalimpsestDb.GENERATION }
                ?.let {
                    return State.Blocked(OpenResult.Incompatible(it.generation))
                }
            val heads = graph.heads
            if (heads.size > 1)
                return State.Blocked(
                    OpenResult.Diverged(
                        heads.map {
                            OpenResult.Diverged.Branch(it.session.toString(), it.writtenAt)
                        }
                    )
                )
            val head = heads.single()
            val missing =
                head.files(layout).filter { (path, length) ->
                    length > 0 && (!path.exists() || path.fileSize() < length)
                }
            if (missing.isNotEmpty())
                return State.Blocked(
                    OpenResult.SyncIncomplete(missing.map { layout.relative(it.first) })
                )
            return State.Ready(head)
        }
    }
}
