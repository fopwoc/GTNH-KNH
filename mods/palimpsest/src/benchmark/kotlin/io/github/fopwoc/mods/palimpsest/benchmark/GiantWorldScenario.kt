package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageCache
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A world the size of a long GTNH save: every tile observed a few times, one small base edited
 * relentlessly, with a reader and a maintenance thread running while the base is written.
 */
internal object GiantWorldScenario {
    const val BASE_SIDE = 8
    private const val COLD_EDITS = 2
    private const val EDITS_PER_EPOCH = 8
    private const val EPOCHS_PER_APPEND = 100
    /** Paces the writer so seals and compactions interleave with appends as they would in play. */
    private const val APPEND_PACING_MILLIS = 20L
    private const val TIME_LAPSE_STEPS = 10

    class Latency(val samples: Int, val p50Micros: Long, val p99Micros: Long, val maxMicros: Long) {
        override fun toString() =
            "samples=$samples p50_us=$p50Micros p99_us=$p99Micros max_us=$maxMicros"

        companion object {
            fun of(nanos: List<Long>): Latency {
                if (nanos.isEmpty()) return Latency(0, 0, 0, 0)
                val sorted = nanos.sorted()
                return Latency(
                    sorted.size,
                    sorted[sorted.size / 2] / 1_000,
                    sorted[(sorted.size * 99) / 100] / 1_000,
                    sorted.last() / 1_000,
                )
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
    fun run(
        directory: Path,
        side: Int,
        hotEpochs: Int,
        log: (String) -> Unit,
        shouldStop: () -> Boolean,
        onProgress: (String) -> Unit,
    ): Boolean {
        val regionSide = RegionTileHistoryStore.REGION_TILES
        require(side % regionSide == 0 && side >= 2 * regionSide && hotEpochs >= EPOCHS_PER_APPEND)
        val regions = side / regionSide
        val baseOrigin = side / 2 - BASE_SIDE / 2
        val baseTiles = buildList {
            for (z in 0 until BASE_SIDE) for (x in 0 until BASE_SIDE) add(
                TileKey(baseOrigin + x, baseOrigin + z)
            )
        }
        log(
            "case=giant-world tiles=${side.toLong() * side} regions=${regions * regions} base_tiles=${baseTiles.size} hot_epochs=$hotEpochs cold_edits=$COLD_EDITS"
        )

        // Cold world: the whole map gets observed once, then touched COLD_EDITS times at scattered
        // epochs.
        val coldStart = System.nanoTime()
        var coldLayers = 0L
        val coldEpochSpan = hotEpochs.toLong() * 4
        RegionTileHistoryStore(directory).use { store ->
            val random = Random(1)
            for (regionZ in 0 until regions) for (regionX in 0 until regions) {
                if (shouldStop()) return false
                val layers = ArrayList<TileLayer>(regionSide * regionSide * (1 + COLD_EDITS))
                val edits = ArrayList<TileLayer>()
                for (localZ in 0 until regionSide) for (localX in 0 until regionSide) {
                    val key = TileKey(regionX * regionSide + localX, regionZ * regionSide + localZ)
                    val colors = coldColors(key)
                    val first = random.nextInt(1000).toLong()
                    layers += TileLayer.full(key, first, colors)
                    var epoch = first
                    var current = colors
                    repeat(COLD_EDITS) {
                        epoch += 1 + random.nextInt((coldEpochSpan / (COLD_EDITS + 1)).toInt())
                        val next =
                            current.copyOf().apply {
                                this[random.nextInt(TileLayer.PIXELS)] =
                                    (random.nextInt(255) + 1).toByte()
                            }
                        TileLayer.changed(key, epoch, current, next)?.let {
                            edits += it
                            current = next
                        }
                    }
                }
                coldLayers += store.append(layers).layersWritten
                coldLayers += store.append(edits.sortedBy(TileLayer::epoch)).layersWritten
                store.sealDue()
                if ((regionZ * regions + regionX) % 64 == 63) {
                    onProgress(
                        "Giant world: ${(regionZ * regions + regionX + 1)}/${regions * regions} cold regions"
                    )
                }
            }
            store.flush()
        }
        log(
            "cold_generate_nanos=${System.nanoTime() - coldStart} cold_layers=$coldLayers ${footprint(directory)}"
        )

        // Hot base: a long edit history on a few tiles while a reader and maintenance run
        // alongside.
        val hotStart = System.nanoTime()
        val readerLatency = ArrayList<Long>()
        val maintenanceLatency = ArrayList<Long>()
        var sealsDuringHot = 0
        var compactionsDuringHot = 0
        val appendLatency = ArrayList<Long>()
        var hotLayers = 0L
        var latestEpoch = coldEpochSpan
        RegionTileHistoryStore(directory).use { store ->
            val palette = IntArray(256) { it * 0x010101 }
            val stop = AtomicBoolean(false)
            val failure = AtomicReference<Throwable>()
            val reader =
                thread(name = "giant-reader") {
                    try {
                        val cache = pageCache(store, palette)
                        val page = MapPageKey.containingTile(baseOrigin, baseOrigin, 0)
                        while (!stop.get()) {
                            val start = System.nanoTime()
                            cache.invalidateTiles(baseTiles, 0)
                            checkNotNull(cache.latest(page))
                            synchronized(readerLatency) {
                                readerLatency += System.nanoTime() - start
                            }
                        }
                    } catch (problem: Throwable) {
                        failure.compareAndSet(null, problem)
                    }
                }
            val maintainer =
                thread(name = "giant-maintenance") {
                    try {
                        while (!stop.get()) {
                            val start = System.nanoTime()
                            sealsDuringHot += store.sealDue()
                            compactionsDuringHot += store.compact()
                            val elapsed = System.nanoTime() - start
                            if (elapsed > 1_000_000) {
                                synchronized(maintenanceLatency) { maintenanceLatency += elapsed }
                            }
                            Thread.sleep(50)
                        }
                    } catch (problem: Throwable) {
                        failure.compareAndSet(null, problem)
                    }
                }
            val random = Random(2)
            val current = HashMap<TileKey, ByteArray>()
            for (key in baseTiles) current[key] = checkNotNull(store.read(key, latestEpoch)).colors
            var epoch = latestEpoch
            var appended = 0
            while (appended < hotEpochs) {
                if (shouldStop()) {
                    stop.set(true)
                    return false
                }
                val batch = ArrayList<TileLayer>(EPOCHS_PER_APPEND * EDITS_PER_EPOCH)
                repeat(EPOCHS_PER_APPEND) {
                    epoch++
                    val touched = HashSet<TileKey>()
                    while (touched.size < EDITS_PER_EPOCH) touched +=
                        baseTiles[random.nextInt(baseTiles.size)]
                    for (key in touched) {
                        val previous = current.getValue(key)
                        val next = previous.copyOf()
                        repeat(1 + random.nextInt(16)) {
                            next[random.nextInt(TileLayer.PIXELS)] =
                                (random.nextInt(255) + 1).toByte()
                        }
                        TileLayer.changed(key, epoch, previous, next)?.let { batch += it }
                        current[key] = next
                    }
                }
                val start = System.nanoTime()
                hotLayers += store.append(batch).layersWritten
                appendLatency += System.nanoTime() - start
                appended += EPOCHS_PER_APPEND
                Thread.sleep(APPEND_PACING_MILLIS)
                if (appended % (EPOCHS_PER_APPEND * 50) == 0)
                    onProgress("Giant world: base $appended/$hotEpochs epochs")
            }
            latestEpoch = epoch
            stop.set(true)
            reader.join()
            maintainer.join()
            failure.get()?.let { throw it }
            store.flush()
            log(
                "hot_generate_nanos=${System.nanoTime() - hotStart} hot_layers=$hotLayers latest_epoch=$latestEpoch open_regions=${store.openRegionCount()} resident_index_bytes=${store.openIndexArrayBytes()}"
            )
            log("hot_append ${Latency.of(appendLatency)}")
            log("hot_concurrent_reader ${Latency.of(readerLatency)}")
            log(
                "hot_maintenance seals=$sealsDuringHot compactions=$compactionsDuringHot ${Latency.of(maintenanceLatency)}"
            )
            for (key in baseTiles.take(4)) {
                check(store.read(key, latestEpoch)!!.colors.contentEquals(current.getValue(key))) {
                    "Base tile $key drifted"
                }
            }
        }
        log("after_hot ${footprint(directory)}")

        // Fresh process view: cold open, base viewport, then a time-lapse over the whole world.
        RegionTileHistoryStore(directory).use { store ->
            val palette = IntArray(256) { it * 0x010101 }
            val cache = pageCache(store, palette)
            val basePage = MapPageKey.containingTile(baseOrigin, baseOrigin, 0)
            val openStart = System.nanoTime()
            checkNotNull(cache.latest(basePage))
            log(
                "cold_open_base_page_nanos=${System.nanoTime() - openStart} regions_opened=${store.regionOpenCount()} segments_hashed=${store.segmentsHashedCount()}"
            )
            for (key in baseTiles) {
                checkNotNull(store.read(key, latestEpoch)) {
                    "Base tile $key unreadable at $latestEpoch; latest=${store.read(key, Long.MAX_VALUE) != null} " +
                        "changes(0,latest)=${store.hasChanges(key, 0, Long.MAX_VALUE)}"
                }
            }
            val baseRead = ArrayList<Long>()
            repeat(50) {
                val start = System.nanoTime()
                for (key in baseTiles) checkNotNull(store.read(key, latestEpoch))
                baseRead += System.nanoTime() - start
            }
            log("base_viewport_latest ${Latency.of(baseRead)}")
            val worldLod = Integer.numberOfTrailingZeros(side / MapPageKey.BASE_TILES)
            val worldPage = MapPageKey(0, 0, worldLod)
            val worldStart = System.nanoTime()
            checkNotNull(cache.latest(worldPage))
            log(
                "world_page_lod=$worldLod cold_nanos=${System.nanoTime() - worldStart} regions_opened=${store.regionOpenCount()} tile_lookups=${cache.tileReadCount()}"
            )
            val stepNanos = ArrayList<Long>()
            val baseStepNanos = ArrayList<Long>()
            for (step in 0 until TIME_LAPSE_STEPS) {
                if (shouldStop()) return false
                val epoch = latestEpoch * (step + 1) / TIME_LAPSE_STEPS
                val start = System.nanoTime()
                checkNotNull(cache.historical(worldPage, epoch))
                stepNanos += System.nanoTime() - start
                val baseStart = System.nanoTime()
                cache.historical(basePage, epoch)
                baseStepNanos += System.nanoTime() - baseStart
            }
            log(
                "time_lapse_world_page steps=$TIME_LAPSE_STEPS first_nanos=${stepNanos.first()} ${Latency.of(stepNanos.drop(1))}"
            )
            log(
                "time_lapse_base_page steps=$TIME_LAPSE_STEPS first_nanos=${baseStepNanos.first()} ${Latency.of(baseStepNanos.drop(1))}"
            )
            log(
                "final open_regions=${store.openRegionCount()} resident_index_bytes=${store.openIndexArrayBytes()} region_opens=${store.regionOpenCount()} evictions=${store.regionEvictionCount()}"
            )
        }
        log("case_status=PASS case=giant-world")
        return true
    }

    private fun pageCache(store: RegionTileHistoryStore, palette: IntArray) =
        MapPageCache(
            { key, epoch -> store.read(key, epoch)?.colors },
            palette,
            hasChanged = store::hasChanges,
            readSamples = { key, epoch, positions ->
                store.readSamples(key, epoch, positions)?.colors
            },
        )

    private fun coldColors(key: TileKey) =
        ByteArray(TileLayer.PIXELS) {
            ((key.x * 31 + key.z * 17 + it / 16 * 3 + it % 16) and 255).toByte()
        }

    private fun footprint(directory: Path): String {
        var segments = 0L
        var segmentBytes = 0L
        var sidecarBytes = 0L
        var logBytes = 0L
        Files.walk(directory).use { paths ->
            paths.filter(Files::isRegularFile).forEach { file ->
                val name = file.fileName.toString()
                val size = Files.size(file)
                when {
                    name.endsWith(".pseg") -> {
                        segments++
                        segmentBytes += size
                    }
                    name.endsWith(".pidx") -> sidecarBytes += size
                    name.endsWith(".wal") -> logBytes += size
                }
            }
        }
        return "segments=$segments segment_bytes=$segmentBytes sidecar_bytes=$sidecarBytes log_bytes=$logBytes"
    }
}
