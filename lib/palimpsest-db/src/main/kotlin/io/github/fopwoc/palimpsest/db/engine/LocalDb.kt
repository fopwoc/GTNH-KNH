package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.DbLog
import io.github.fopwoc.palimpsest.db.Depth
import io.github.fopwoc.palimpsest.db.Dimension
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.IndexReplay
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import io.github.fopwoc.palimpsest.db.store.VocabularyFile
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
    override val vocabulary: VocabularyFile,
) : PalimpsestDb {
    val log: DbLog = config.log
    private val cacheDirectory = config.cacheDirectory
    val threads = DbThreads(config)
    val kinds = KindTable(vocabulary, config.blockKinds)
    private val dimensions = ConcurrentHashMap<DimensionId, VolumeDimension>()
    private var lastFlush = System.nanoTime()

    @Volatile private var closed = false

    override fun dimension(id: DimensionId, mode: DimensionMode): Dimension {
        check(!closed) { "Database is closed" }
        return dimensions[id]
            ?: synchronized(dimensions) { dimensions.getOrPut(id) { load(id, mode) } }
    }

    override fun flush() {
        check(!closed) { "Database is closed" }
        dimensions.values.forEach(VolumeDimension::drain)
        threads.writer.submit { flushNow(seal = false) }.get()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            dimensions.values.forEach(VolumeDimension::drain)
            threads.writer.submit { flushNow(seal = true) }.get()
            log.log(LogLevel.INFO, "open", "Closed $session", null)
        } finally {
            threads.close()
            dimensions.values.forEach(VolumeDimension::closeFiles)
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

    /** Writer thread only: data first, then the manifest that commits it. */
    private fun flushNow(seal: Boolean) {
        vocabulary.force()
        dimensions.values.forEach(VolumeDimension::force)
        val touched = dimensions.values.associate { it.id to it.entry(seal) }
        val untouched = previous?.dimensions.orEmpty().filter { it.id !in touched }
        Manifest(
                generation = PalimpsestDb.GENERATION,
                session = session,
                parent = previous?.session,
                vocabularyLength = vocabulary.length,
                dimensions = untouched + touched.values,
            )
            .write(layout)
        dimensions.values.forEach(VolumeDimension::flushIndex)
        lastFlush = System.nanoTime()
    }

    private fun load(id: DimensionId, mode: DimensionMode): VolumeDimension {
        val entry = previous?.dimensions?.firstOrNull { it.id == id }
        if (entry != null)
            require(entry.mode == mode) {
                "$id is stored as ${entry.mode}; changing modes is not supported yet"
            }
        if (mode.depth == Depth.SURFACE)
            TODO("SURFACE dimensions keep 2D summaries as truth; they come with the index layer")
        val started = System.nanoTime()
        val segments =
            entry?.segments.orEmpty().mapIndexed { ordinal, stored ->
                // An unsealed segment is a crashed session's: cut it to what the manifest
                // committed.
                SegmentFile.open(
                    layout.segment(id, stored.name),
                    ordinal,
                    stored.length,
                    writable = !stored.sealed,
                )
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
        return VolumeDimension(id, mode, this, segments, index)
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
                check(layout)?.let {
                    return it
                }
                val previous = if (layout.manifest.exists()) Manifest.read(layout) else null
                val session =
                    Manifest.Session(
                        (previous?.session?.number ?: 0) + 1,
                        "%08x".format(SecureRandom().nextInt()),
                    )
                val vocabulary =
                    VocabularyFile.open(layout.vocabulary, previous?.vocabularyLength ?: 0)
                config.log.log(LogLevel.INFO, "open", "Opened $world as session $session", null)
                return OpenResult.Opened(
                        LocalDb(layout, config, lock, previous, session, vocabulary)
                    )
                    .also { opened = true }
            } finally {
                if (!opened) lock.close()
            }
        }

        /** Why the folder cannot be opened as it is, or null when it can. */
        private fun check(layout: WorldLayout): OpenResult? {
            if (!layout.manifest.exists()) {
                val others =
                    layout.root.listDirectoryEntries().filter { it.name != layout.lock.name }
                return when {
                    others.isEmpty() -> null
                    layout.vocabulary.exists() || layout.root.resolve("dimensions").exists() ->
                        OpenResult.SyncIncomplete(listOf(layout.relative(layout.manifest)))
                    else -> OpenResult.Incompatible(generation = 0)
                }
            }
            val generation = Manifest.generation(layout)
            if (generation != PalimpsestDb.GENERATION) return OpenResult.Incompatible(generation)
            val manifest = Manifest.read(layout)
            val expected =
                listOf(layout.vocabulary to manifest.vocabularyLength) +
                    manifest.dimensions.flatMap { dimension ->
                        dimension.segments.map {
                            layout.segment(dimension.id, it.name) to it.length
                        }
                    }
            val missing = expected.filter { (path, length) ->
                length > 0 && (!path.exists() || path.fileSize() < length)
            }
            // Not detected yet: sealed segments the manifest does not name mean that two computers
            // continued the history apart (OpenResult.Diverged).
            return if (missing.isEmpty()) null
            else OpenResult.SyncIncomplete(missing.map { layout.relative(it.first) })
        }
    }
}
