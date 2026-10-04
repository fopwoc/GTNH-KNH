package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Activity
import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.DbLog
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.store.BranchArchive
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.ManifestGraph
import io.github.fopwoc.palimpsest.db.store.VocabularyStore
import io.github.fopwoc.palimpsest.db.store.WorldLayout
import io.github.fopwoc.palimpsest.db.store.WorldLock
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
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
    val cacheDirectory: Path = config.cacheDirectory
    private val loader = DimensionLoader(this)
    val threads = DbThreads(config)
    val kinds = KindTable(vocabulary, config.blockKinds)
    val activities = ActivityBoard()
    private val dimensions = ConcurrentHashMap<DimensionId, LocalDimension>()
    private val retentions =
        ConcurrentHashMap<DimensionId, Retention>(
            previous?.dimensions.orEmpty().associate { it.id to it.retention }
        )
    private val dropped: MutableSet<DimensionId> = ConcurrentHashMap.newKeySet()
    private val dropping = ConcurrentHashMap<DimensionId, CompletableFuture<Unit>>()
    private var lastFlush = System.nanoTime()

    @Volatile private var closed = false

    override val activity: List<Activity>
        get() = activities.snapshot()

    override fun dimension(id: DimensionId): Dimension {
        check(!closed) { "Database is closed" }
        dimensions[id]?.let {
            return it
        }
        synchronized(dimensions) {
            dimensions[id]?.let {
                return it
            }
            val entry = previous?.dimensions?.firstOrNull { it.id == id }?.takeIf { id !in dropped }
            // A dimension being dropped is deleted before it starts again, empty.
            val dropping = dropping[id]
            return LocalDimension(id, this) {
                    dropping?.join()
                    loader.load(id, entry)
                }
                .also { dimensions[id] = it }
        }
    }

    override fun setRetention(id: DimensionId, retention: Retention) {
        check(!closed) { "Database is closed" }
        retentions[id] = retention
    }

    /** What [id] keeps: as set this session, else as stored, else everything. */
    fun retention(id: DimensionId): Retention = retentions[id] ?: Retention.HISTORY

    override fun drop(id: DimensionId): CompletableFuture<Unit> {
        check(!closed) { "Database is closed" }
        synchronized(dimensions) {
            val dimension = dimensions.remove(id)
            val stored =
                previous?.dimensions?.firstOrNull { it.id == id }?.takeIf { id !in dropped }
            dropped += id
            retentions.remove(id)
            val done =
                CompletableFuture.supplyAsync(
                    {
                        dimension?.drain()
                        dimension?.retire()
                        // The manifest forgets the dimension first; only then do its files go.
                        threads.writer.submit { flushNow(seal = false) }.get()
                        dimension?.closeFiles()
                        val files =
                            stored?.segments.orEmpty().map { it.name } +
                                dimension?.segmentNames().orEmpty()
                        files.forEach { Files.deleteIfExists(layout.segment(id, it)) }
                        cacheDirectory.resolve(id.key).toFile().deleteRecursively()
                        log.log(LogLevel.INFO, "open", "Dropped $id", null)
                    },
                    threads.background,
                )
            dropping[id] = done
            return done
        }
    }

    override fun closeAsync(): CompletableFuture<Unit> {
        val done = CompletableFuture<Unit>()
        Thread(
                { runCatching(::close).fold(done::complete, done::completeExceptionally) },
                "palimpsest-db-close",
            )
            .start()
        return done
    }

    /** Files compaction replaced: gone once a manifest naming the compacted segment is on disk. */
    fun retire(files: List<Path>) {
        threads.writer.execute {
            flushNow(seal = false)
            files.forEach(Files::deleteIfExists)
        }
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
            // A drop flushes and deletes through the pools that are about to stop.
            dropping.values.forEach { it.handle { _, _ -> }.join() }
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
    private fun flushNow(seal: Boolean) =
        activities.start(Activity.Task.FLUSHING, null, null).use { flushNowTracked(seal) }

    private fun flushNowTracked(seal: Boolean) {
        vocabulary.force()
        dimensions.values.forEach(LocalDimension::force)
        // A dimension still loading keeps the entry the previous manifest has for it.
        val touched = dimensions.values.mapNotNull { it.entry(seal) }.associateBy { it.id }
        val untouched =
            previous
                ?.dimensions
                .orEmpty()
                .filter { it.id !in touched && it.id !in dropped }
                .map { it.copy(retention = retention(it.id)) }
        // Set for dimensions not opened this session: kept so the setting survives.
        val known = touched.keys + untouched.map { it.id }
        val unopened =
            retentions.keys
                .filter { it !in known }
                .map { Manifest.DimensionEntry(it, retention(it), emptyList()) }
        Manifest(
                generation = PalimpsestDb.GENERATION,
                session = session,
                parent = previous?.session,
                writtenAt = System.currentTimeMillis(),
                vocabulary = vocabulary.entries(seal),
                dimensions = untouched + touched.values + unopened,
            )
            .write(layout)
        // This session is the head now; its parent's manifest only names files ours names too.
        previous?.let { Files.deleteIfExists(layout.manifest(it.session)) }
        dimensions.values.forEach(LocalDimension::flushIndex)
        lastFlush = System.nanoTime()
    }

    companion object {
        private val FLUSH_EVERY = TimeUnit.SECONDS.toNanos(30)

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
