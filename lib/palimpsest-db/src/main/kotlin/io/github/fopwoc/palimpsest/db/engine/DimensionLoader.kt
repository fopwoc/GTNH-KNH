package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Activity
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.index.DimensionIndex
import io.github.fopwoc.palimpsest.db.index.IndexRemap
import io.github.fopwoc.palimpsest.db.index.IndexReplay
import io.github.fopwoc.palimpsest.db.store.Compactor
import io.github.fopwoc.palimpsest.db.store.Manifest
import io.github.fopwoc.palimpsest.db.store.SegmentFile
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** How a dimension of [db] gets ready: its history compacted when due, its index made to fit it. */
internal class DimensionLoader(private val db: LocalDb) {
    /**
     * Loads a dimension on the background pool: compaction when due, then the index caught up with
     * history or rebuilt from it, reporting progress in bytes of history as it goes.
     */
    fun load(id: DimensionId, entry: Manifest.DimensionEntry?): LocalDimension.Loaded {
        val retention = db.retention(id)
        val started = System.nanoTime()
        val files = entry?.segments.orEmpty()
        val stored = files.mapIndexed { ordinal, file ->
            SegmentFile.open(db.layout.segment(id, file.name), ordinal, file.length)
        }
        var segments = stored
        var superseded = emptyList<Path>()
        val directory = db.cacheDirectory.resolve(id.key)
        val kindOrdinal = { kindId: Int ->
            // Ids the db.vocabulary no longer has: the index saw history a crash took back.
            if (kindId < db.vocabulary.size) db.kinds.kind(kindId).ordinal else -1
        }
        if (compactionDue(retention, files)) {
            // An index holding all of the old history can follow the compaction instead of a
            // rebuild.
            val before = DimensionIndex.coverage(directory, kindOrdinal)
            val complete =
                before != null &&
                    before.segments.size == files.size &&
                    before.segments.zip(files).all { (covered, file) ->
                        covered.name == file.name && covered.length == file.length
                    }
            val target = db.layout.segment(id, "$db.session${Compactor.SUFFIX}")
            val compaction =
                db.activities.start(Activity.Task.COMPACTING, id, files.sumOf { it.length }).use {
                    progress ->
                    val compactor = Compactor(stored, progress::advance)
                    when (retention) {
                        Retention.HISTORY -> compactor.history(target, id, db.session)
                        Retention.LATEST -> compactor.latest(target, id, db.session)
                    }
                }
            val compacted = compaction.segment
            compacted.force()
            stored.forEach(SegmentFile::close)
            segments = listOf(compacted)
            superseded = files.map { db.layout.segment(id, it.name) }
            if (complete)
                db.activities
                    .start(
                        Activity.Task.REMAPPING_INDEX,
                        id,
                        IndexRemap.regions(directory).toLong(),
                    )
                    .use { progress ->
                        IndexRemap.apply(directory, retention, compaction, progress::advance)
                    }
            db.log.log(
                LogLevel.INFO,
                "open",
                "$id: compacted ${files.size} segments, ${files.sumOf { it.length }} bytes into ${compacted.length}; " +
                    if (complete) "index moved along" else "index will be rebuilt",
                null,
            )
        }
        val coverage = DimensionIndex.coverage(directory, kindOrdinal)
        val fits =
            coverage != null &&
                coverage.segments.size <= segments.size &&
                coverage.segments.withIndex().all { (ordinal, covered) ->
                    covered.name == segments[ordinal].name &&
                        covered.length <= segments[ordinal].length
                }
        val index = DimensionIndex.open(directory, REGIONS, fresh = !fits)
        val task = if (fits) Activity.Task.CATCHING_UP_INDEX else Activity.Task.REBUILDING_INDEX
        val replay = IndexReplay(index, segments, db.kinds::kind)
        val frames =
            db.activities.start(task, id, replay.pending).use { progress ->
                IndexReplay(index, segments, db.kinds::kind, progress::advance).run()
            }
        if (frames > 0) index.flush(db.kinds.snapshot(db.vocabulary.size))
        db.log.log(
            LogLevel.INFO,
            "open",
            "$id: ${index.commits.size} commits, ${if (fits) "index caught up" else "index rebuilt"} " +
                "from $frames frames in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms",
            null,
        )
        return LocalDimension.Loaded(segments, index, superseded)
    }

    /**
     * Whether to rewrite the dimension's history before this db.session writes: a latest-only one
     * once history grew half the size of its last compaction, a full one once it is many small
     * files.
     */
    private fun compactionDue(retention: Retention, files: List<Manifest.FileEntry>): Boolean {
        if (files.size < 2) return false
        return when (retention) {
            Retention.HISTORY -> files.size >= HISTORY_SEGMENTS
            Retention.LATEST -> {
                val base = files.first().takeIf { it.name.endsWith(Compactor.SUFFIX) }?.length ?: 0
                (files.sumOf { it.length } - base) * 2 >= base
            }
        }
    }

    private companion object {
        /** Index regions kept in memory per dimension: an 8×8-region area, far beyond any view. */
        const val REGIONS = 64

        /** Sessions' segments a full-history dimension collects before they are merged into one. */
        const val HISTORY_SEGMENTS = 16
    }
}
