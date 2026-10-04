package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.Commit
import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.Depth
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.WorldTick
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/** Runs one scenario against a fresh world folder and measures writing, reopening and reading. */
class Runner(
    private val root: Path,
    private val backgroundThreads: Int,
    private val interactiveThreads: Int,
) {
    class Result(
        val commits: Int,
        val observed: Long,
        val versions: Long,
        val sectionsWritten: Long,
        val disk: Long,
        val commit: Stats,
        val close: Long,
        val open: Long,
        val firstRead: Stats,
        val warmRead: Stats,
        val pastRead: Stats,
        val parallelRead: Long,
        val probes: Int,
        /** One playback step: the diff between neighbouring commits. */
        val step: Stats,
        /** The diff across the whole history. */
        val wholeDiff: Long,
        val wholeChanges: Int,
    )

    fun run(kind: Scenario.Kind): Result {
        val world = root.resolve(kind.name)
        world.toFile().deleteRecursively()
        var observed = 0L
        var versions = 0L
        var sectionsWritten = 0L
        val commit = Stats()
        val scenario: Scenario
        val close: Long
        var seenAtMiddle = emptySet<ChunkPos>()
        open(world).let { db ->
            scenario = kind.create(db.vocabulary)
            val dimension = db.dimension(DIMENSION, MODE)
            repeat(scenario.commits) { index ->
                val observations = scenario.observe(index)
                observed += observations.size
                lateinit var written: Commit
                commit.add(timed { written = dimension.commit(tick(index), observations).get() })
                if (index == scenario.commits / 2 - 1) seenAtMiddle = scenario.seen.toSet()
                versions += written.chunksChanged
                sectionsWritten += written.sectionsWritten
            }
            close = timed { db.close() }
        }
        val disk =
            Files.walk(world).use { files ->
                files.filter(Files::isRegularFile).mapToLong(Files::size).sum()
            }

        val probes = scenario.seen.shuffled(Random(1)).take(PROBES)
        val firstRead = Stats()
        val warmRead = Stats()
        val pastRead = Stats()
        var parallelRead = 0L
        val step = Stats()
        var wholeDiff = 0L
        var wholeChanges = 0
        lateinit var db: PalimpsestDb
        val open = timed {
            db = open(world)
            db.dimension(DIMENSION, MODE).ready.get()
        }
        db.use {
            val dimension = db.dimension(DIMENSION, MODE)
            val latest = dimension.at(tick(scenario.commits))
            val past = dimension.at(tick(scenario.commits / 2))
            for (pos in probes) firstRead.add(timed { latest.volume(pos).result.get() })
            for (pos in probes) warmRead.add(timed { latest.volume(pos).result.get() })
            for (pos in seenAtMiddle.shuffled(Random(2)).take(PROBES)) pastRead.add(
                timed { past.volume(pos).result.get() }
            )
            val wide = seenAtMiddle.toList()
            parallelRead =
                timed { wide.map { past.volume(it) }.forEach { it.result.get() } } /
                    wide.size.coerceAtLeast(1)
            for (index in 1 until minOf(scenario.commits, STEPS)) step.add(
                timed { dimension.diff(tick(index - 1), tick(index)).result.get() }
            )
            wholeDiff = timed {
                wholeChanges =
                    dimension.diff(WorldTick(0), tick(scenario.commits)).result.get().changes.size
            }
        }
        return Result(
            scenario.commits,
            observed,
            versions,
            sectionsWritten,
            disk,
            commit,
            close,
            open,
            firstRead,
            warmRead,
            pastRead,
            parallelRead,
            probes.size,
            step,
            wholeDiff,
            wholeChanges,
        )
    }

    private fun open(world: Path): PalimpsestDb {
        val config =
            DbConfig(
                cacheDirectory = root.resolve("cache"),
                blockKinds = { BlockKind.SOLID },
                log = { level, tag, message, error ->
                    if (level >= LogLevel.WARN)
                        System.err.println("[$level/$tag] $message ${error ?: ""}")
                },
                interactiveThreads = interactiveThreads,
                backgroundThreads = backgroundThreads,
            )
        return when (val result = PalimpsestDb.open(world, config)) {
            is OpenResult.Opened -> result.db
            else -> error("Cannot open $world: $result")
        }
    }

    private companion object {
        val DIMENSION = DimensionId("overworld")
        val MODE = DimensionMode(Depth.VOLUME, Retention.HISTORY)
        const val PROBES = 200
        const val STEPS = 100

        /** One commit a minute of game time. */
        fun tick(index: Int) = WorldTick((index + 1) * 1200L)
    }
}
