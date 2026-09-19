package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A world the size of a long GTNH save: every tile observed, then revisited area by area, one
 * small base edited relentlessly with a reader and a sealer running while the base is written.
 */
internal object GiantWorldScenario {
    const val BASE_SIDE = 8
    private const val AREA_SIDE = 32
    private const val COLD_REVISITS = 2
    private const val EDITS_PER_EPOCH = 8
    private const val COMMITS_PER_PAUSE = 100
    /** Paces the writer so seals interleave with commits as they would in play. */
    private const val PAUSE_MILLIS = 20L
    private const val TIME_LAPSE_STEPS = 10

    class Latency(val samples: Int, val p50Micros: Long, val p99Micros: Long, val maxMicros: Long) {
        override fun toString() = "samples=$samples p50_us=$p50Micros p99_us=$p99Micros max_us=$maxMicros"

        companion object {
            fun of(nanos: List<Long>): Latency {
                if (nanos.isEmpty()) return Latency(0, 0, 0, 0)
                val sorted = nanos.sorted()
                return Latency(sorted.size, sorted[sorted.size / 2] / 1_000, sorted[(sorted.size * 99) / 100] / 1_000, sorted.last() / 1_000)
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
    fun run(directory: Path, side: Int, hotEpochs: Int, log: (String) -> Unit, shouldStop: () -> Boolean, onProgress: (String) -> Unit): Boolean {
        require(side % AREA_SIDE == 0 && side >= 2 * AREA_SIDE && hotEpochs >= COMMITS_PER_PAUSE)
        val areas = side / AREA_SIDE
        val baseOrigin = side / 2 - BASE_SIDE / 2
        val baseTiles = buildList { for (z in 0 until BASE_SIDE) for (x in 0 until BASE_SIDE) add(TileKey(baseOrigin + x, baseOrigin + z)) }
        log("case=giant-world tiles=${side.toLong() * side} areas=${areas * areas} base_tiles=${baseTiles.size} hot_epochs=$hotEpochs cold_revisits=$COLD_REVISITS")

        // Cold world: every area observed once, then revisited with a few edits, area by area.
        val coldStart = System.nanoTime()
        var coldTiles = 0L
        var coldNodes = 0L
        var epoch = 0L
        BenchmarkWorld(directory).use { world ->
            val random = Random(1)
            for (areaZ in 0 until areas) for (areaX in 0 until areas) {
                if (shouldStop()) return false
                val changes = HashMap<TileKey, TileRecord>(AREA_SIDE * AREA_SIDE)
                for (localZ in 0 until AREA_SIDE) for (localX in 0 until AREA_SIDE) {
                    val key = TileKey(areaX * AREA_SIDE + localX, areaZ * AREA_SIDE + localZ)
                    changes[key] = TileRecord.build(epoch, coldBlocks(key)::get, { 64 }, { 0 }, { 1 })
                }
                val result = world.tree.commit(epoch++, changes)
                coldTiles += result.tilesWritten
                coldNodes += result.nodesWritten
                world.tree.sealIfDue()
                if ((areaZ * areas + areaX) % 64 == 63) onProgress("Giant world: ${areaZ * areas + areaX + 1}/${areas * areas} cold areas")
            }
            repeat(COLD_REVISITS) { revisit ->
                for (areaZ in 0 until areas) for (areaX in 0 until areas) {
                    if (shouldStop()) return false
                    val edits = HashMap<TileKey, TileRecord>()
                    repeat(AREA_SIDE) {
                        val key = TileKey(areaX * AREA_SIDE + random.nextInt(AREA_SIDE), areaZ * AREA_SIDE + random.nextInt(AREA_SIDE))
                        val current = edits[key] ?: checkNotNull(world.tree.tile(key, Long.MAX_VALUE))
                        edits[key] = current.with(epoch, intArrayOf(random.nextInt(TileRecord.PIXELS)), arrayOf(intArrayOf(1 + random.nextInt(255)), intArrayOf(64), intArrayOf(0), intArrayOf(1)))
                    }
                    val result = world.tree.commit(epoch++, edits)
                    coldTiles += result.tilesWritten
                    coldNodes += result.nodesWritten
                    world.tree.sealIfDue()
                }
                onProgress("Giant world: revisit ${revisit + 1}/$COLD_REVISITS done")
            }
            world.tree.seal()
        }
        log("cold_generate_nanos=${System.nanoTime() - coldStart} cold_commits=$epoch cold_tiles=$coldTiles cold_nodes=$coldNodes ${BenchmarkStorageSuite.footprint(directory)}")

        // Hot base: a long edit history on a few tiles while a reader and a sealer run alongside.
        val hotStart = System.nanoTime()
        val readerLatency = ArrayList<Long>()
        val sealLatency = ArrayList<Long>()
        var sealsDuringHot = 0
        val commitLatency = ArrayList<Long>()
        var hotTiles = 0L
        var hotNodes = 0L
        var hotBytes = 0L
        val current = HashMap<TileKey, TileRecord>()
        BenchmarkWorld(directory).use { world ->
            val stop = AtomicBoolean(false)
            val failure = AtomicReference<Throwable>()
            val reader =
                thread(name = "giant-reader") {
                    try {
                        val page = MapPageKey.containingTile(baseOrigin, baseOrigin, 0)
                        while (!stop.get()) {
                            val start = System.nanoTime()
                            world.pages.invalidateTiles(baseTiles, 0)
                            checkNotNull(world.pages.latest(page))
                            synchronized(readerLatency) { readerLatency += System.nanoTime() - start }
                        }
                    } catch (problem: Throwable) {
                        failure.compareAndSet(null, problem)
                    }
                }
            val sealer =
                thread(name = "giant-sealer") {
                    try {
                        while (!stop.get()) {
                            val start = System.nanoTime()
                            if (world.tree.sealIfDue()) {
                                sealsDuringHot++
                                synchronized(sealLatency) { sealLatency += System.nanoTime() - start }
                            }
                            Thread.sleep(50)
                        }
                    } catch (problem: Throwable) {
                        failure.compareAndSet(null, problem)
                    }
                }
            val random = Random(2)
            for (key in baseTiles) current[key] = checkNotNull(world.tree.tile(key, Long.MAX_VALUE))
            var committed = 0
            while (committed < hotEpochs) {
                if (shouldStop()) {
                    stop.set(true)
                    return false
                }
                val touched = HashSet<TileKey>()
                while (touched.size < EDITS_PER_EPOCH) touched += baseTiles[random.nextInt(baseTiles.size)]
                val changes = HashMap<TileKey, TileRecord>()
                for (key in touched) {
                    val positions = IntArray(1 + random.nextInt(16)) { random.nextInt(TileRecord.PIXELS) }.distinct().toIntArray()
                    val next = current.getValue(key).with(epoch, positions, arrayOf(IntArray(positions.size) { 1 + random.nextInt(255) }, IntArray(positions.size) { 64 }, IntArray(positions.size), IntArray(positions.size) { 1 }))
                    current[key] = next
                    changes[key] = next
                }
                val start = System.nanoTime()
                val result = world.tree.commit(epoch++, changes)
                commitLatency += System.nanoTime() - start
                hotTiles += result.tilesWritten
                hotNodes += result.nodesWritten
                hotBytes += result.bytes
                committed++
                if (committed % COMMITS_PER_PAUSE == 0) Thread.sleep(PAUSE_MILLIS)
                if (committed % (COMMITS_PER_PAUSE * 50) == 0) onProgress("Giant world: base $committed/$hotEpochs epochs")
            }
            stop.set(true)
            reader.join()
            sealer.join()
            failure.get()?.let { throw it }
            world.tree.seal()
            log("hot_generate_nanos=${System.nanoTime() - hotStart} hot_commits=$hotEpochs hot_tiles=$hotTiles hot_nodes=$hotNodes hot_bytes=$hotBytes latest_epoch=${epoch - 1}")
            log("hot_commit ${Latency.of(commitLatency)}")
            log("hot_concurrent_reader ${Latency.of(readerLatency)}")
            log("hot_seals seals=$sealsDuringHot ${Latency.of(sealLatency)}")
            for (key in baseTiles.take(4)) check(checkNotNull(world.tree.tile(key, Long.MAX_VALUE)).sameFacts(current.getValue(key))) { "Base tile $key drifted" }
        }
        log("after_hot ${BenchmarkStorageSuite.footprint(directory)}")
        val latestEpoch = epoch - 1

        // Fresh process view: cold open, base viewport, then a time-lapse over the whole world.
        BenchmarkWorld(directory).use { world ->
            val basePage = MapPageKey.containingTile(baseOrigin, baseOrigin, 0)
            val openStart = System.nanoTime()
            checkNotNull(world.pages.latest(basePage))
            log("cold_open_base_page_nanos=${System.nanoTime() - openStart} roots=${world.tree.roots.size} nodes_read=${world.tree.nodesRead()}")
            for (key in baseTiles) check(checkNotNull(world.tree.tile(key, latestEpoch)).sameFacts(current.getValue(key))) { "Base tile $key unreadable at $latestEpoch" }
            val baseRead = ArrayList<Long>()
            repeat(50) {
                val start = System.nanoTime()
                for (key in baseTiles) checkNotNull(world.tree.tile(key, latestEpoch))
                baseRead += System.nanoTime() - start
            }
            log("base_viewport_latest ${Latency.of(baseRead)}")
            val worldLod = Integer.numberOfTrailingZeros(side / MapPageKey.BASE_TILES)
            val worldPage = MapPageKey(0, 0, worldLod)
            val nodesBefore = world.tree.nodesRead()
            val worldStart = System.nanoTime()
            checkNotNull(world.pages.latest(worldPage))
            log("world_page_lod=$worldLod cold_nanos=${System.nanoTime() - worldStart} nodes_read=${world.tree.nodesRead() - nodesBefore}")
            val stepNanos = ArrayList<Long>()
            val baseStepNanos = ArrayList<Long>()
            for (step in 0 until TIME_LAPSE_STEPS) {
                if (shouldStop()) return false
                val at = latestEpoch * (step + 1) / TIME_LAPSE_STEPS
                val start = System.nanoTime()
                checkNotNull(world.pages.historical(worldPage, at))
                stepNanos += System.nanoTime() - start
                val baseStart = System.nanoTime()
                world.pages.historical(basePage, at)
                baseStepNanos += System.nanoTime() - baseStart
            }
            log("time_lapse_world_page steps=$TIME_LAPSE_STEPS first_nanos=${stepNanos.first()} ${Latency.of(stepNanos.drop(1))}")
            log("time_lapse_base_page steps=$TIME_LAPSE_STEPS first_nanos=${baseStepNanos.first()} ${Latency.of(baseStepNanos.drop(1))}")
            log("final nodes_read=${world.tree.nodesRead()} tiles_decoded=${world.tree.tilesDecoded()}")
        }
        log("case_status=PASS case=giant-world")
        return true
    }

    private fun coldBlocks(key: TileKey) = IntArray(TileRecord.PIXELS) { 1 + ((key.x * 31 + key.z * 17 + it / 16 * 3 + it % 16) and 255) }
}
