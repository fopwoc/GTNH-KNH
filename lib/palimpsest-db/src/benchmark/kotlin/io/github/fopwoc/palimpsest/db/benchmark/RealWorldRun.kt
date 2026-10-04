package io.github.fopwoc.palimpsest.db.benchmark

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.DbConfig
import io.github.fopwoc.palimpsest.db.Depth
import io.github.fopwoc.palimpsest.db.DimensionId
import io.github.fopwoc.palimpsest.db.DimensionMode
import io.github.fopwoc.palimpsest.db.LogLevel
import io.github.fopwoc.palimpsest.db.OpenResult
import io.github.fopwoc.palimpsest.db.PalimpsestDb
import io.github.fopwoc.palimpsest.db.Retention
import io.github.fopwoc.palimpsest.db.WorldTick
import io.github.fopwoc.palimpsest.db.benchmark.save.LegacySave
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * One dimension of a real save as a single first sight: every populated chunk committed in groups
 * of [PER_COMMIT], regions in order. Then the first regions again, unchanged, for the cost of
 * revisits; then a reopen, the heap the history takes, and read probes. Parsing is not measured.
 * Phases run in separate functions so a finished phase's locals cannot keep its objects alive.
 */
class RealWorldRun(
    private val save: Path,
    private val dimension: String,
    private val root: Path,
    private val threads: Int,
) {
    private class Written(val chunks: Long, val lastTick: Long, val seen: List<ChunkPos>)

    fun run() {
        val world = root.resolve("real-$dimension".replace('/', '-'))
        world.toFile().deleteRecursively()
        val written = write(world)
        val (opened, afterReads) = reopenAndRead(world, written, "reopen")
        val closed = settledHeap()
        println(
            "   heap: open ${bytes((opened - closed).toDouble())}, " +
                "after reads ${bytes((afterReads - closed).toDouble())}"
        )
        root.resolve("cache").toFile().deleteRecursively()
        reopenAndRead(world, written, "rebuild index")
    }

    private fun write(world: Path): Written {
        val commit = Stats()
        val revisit = Stats()
        var chunks = 0L
        var tick = 0L
        val seen = ArrayList<ChunkPos>()
        val started = System.nanoTime()
        var parse = 0L
        open(world).use { db ->
            val reader = LegacySave(save, db.vocabulary)
            val target = db.dimension(DIMENSION, MODE)
            val regions = reader.regions(dimension)
            println("${regions.size} regions in $dimension")
            for (group in regions.chunked(threads)) {
                var parsed = emptyList<List<ChunkObservation>>()
                parse += timed { parsed = group.parallelMap(reader::chunks) }
                for (part in parsed.flatMap { it.chunked(PER_COMMIT) }) {
                    val result = target.commit(WorldTick(++tick), part)
                    commit.add(timed { result.get() })
                    chunks += part.size
                    part.mapTo(seen) { it.pos }
                }
            }
            val again = regions.take(threads).parallelMap(reader::chunks).flatten()
            for (part in again.chunked(PER_COMMIT)) revisit.add(
                timed { target.commit(WorldTick(++tick), part).get() }
            )
            println("   vocabulary ${db.vocabulary.size} identities")
        }
        val disk = diskSize(world)
        println("\n== $dimension: $chunks chunks in ${commit.count} commits of $PER_COMMIT")
        println("   disk ${bytes(disk.toDouble())}, ${bytes(disk.toDouble() / chunks)} per chunk")
        println(
            "   commit p50 ${millis(commit.percentile(0.5).toDouble())}, p95 ${millis(commit.percentile(0.95).toDouble())}, " +
                "max ${millis(commit.percentile(1.0).toDouble())}; ${micros(commit.total.toDouble() / chunks)} per chunk"
        )
        println(
            "   unchanged revisit of $PER_COMMIT chunks: p50 ${millis(revisit.percentile(0.5).toDouble())}, " +
                "p95 ${millis(revisit.percentile(0.95).toDouble())}"
        )
        println(
            "   save parsing ${millis(parse.toDouble())} of ${millis((System.nanoTime() - started).toDouble())} total"
        )
        return Written(chunks, tick, seen)
    }

    /** Reopens and reads; returns the heap in use right after opening and after the reads. */
    private fun reopenAndRead(world: Path, written: Written, label: String): Pair<Long, Long> {
        lateinit var db: PalimpsestDb
        val reopen = timed {
            db = open(world)
            db.dimension(DIMENSION, MODE)
        }
        val opened = settledHeap()
        var afterReads = 0L
        db.use {
            val latest = db.dimension(DIMENSION, MODE).at(WorldTick(written.lastTick))
            val probes = written.seen.shuffled(Random(1)).take(PROBES)
            val first = Stats()
            val warm = Stats()
            for (pos in probes) first.add(timed { latest.volume(pos).result.get() })
            for (pos in probes) warm.add(timed { latest.volume(pos).result.get() })
            val window = written.seen.shuffled(Random(2)).take(1024)
            val batch = timed { window.map { latest.volume(it) }.forEach { it.result.get() } }
            val seenSet = written.seen.toHashSet()
            val center = written.seen[written.seen.size / 3]
            val local = window(center, 16).filter { it in seenSet }
            val localFirst = timed { local.map { latest.volume(it) }.forEach { it.result.get() } }
            val localWarm = timed { local.map { latest.volume(it) }.forEach { it.result.get() } }
            println("   $label ${millis(reopen.toDouble())}")
            println(
                "   local 33×33 window (${local.size} chunks, parallel): first ${millis(localFirst.toDouble())}, " +
                    "again ${millis(localWarm.toDouble())}"
            )
            println(
                "   read chunk: first ${micros(first.mean())}, warm ${micros(warm.mean())}, " +
                    "1024 in parallel ${millis(batch.toDouble())}"
            )
            afterReads = settledHeap()
        }
        return opened to afterReads
    }

    private fun open(world: Path): PalimpsestDb {
        val config =
            DbConfig(
                cacheDirectory = root.resolve("cache"),
                blockKinds = { BlockKind.SOLID },
                log = { level, tag, message, error ->
                    if (level >= LogLevel.WARN) println("   [$level/$tag] $message ${error ?: ""}")
                },
                interactiveThreads = threads,
                backgroundThreads = threads,
            )
        return when (val result = PalimpsestDb.open(world, config)) {
            is OpenResult.Opened -> result.db
            else -> error("Cannot open $world: $result")
        }
    }

    private fun diskSize(world: Path): Long =
        Files.walk(world).use { files ->
            files.filter(Files::isRegularFile).mapToLong(Files::size).sum()
        }

    /** Heap in use right after full collections. */
    private fun settledHeap(): Long {
        repeat(3) {
            System.gc()
            Thread.sleep(200)
        }
        return ManagementFactory.getMemoryPoolMXBeans()
            .filter { it.type == MemoryType.HEAP }
            .sumOf { it.collectionUsage?.used ?: 0 }
    }

    private companion object {
        val DIMENSION = DimensionId("dimension")
        val MODE = DimensionMode(Depth.VOLUME, Retention.HISTORY)
        const val PER_COMMIT = 256
        const val PROBES = 300
    }
}
