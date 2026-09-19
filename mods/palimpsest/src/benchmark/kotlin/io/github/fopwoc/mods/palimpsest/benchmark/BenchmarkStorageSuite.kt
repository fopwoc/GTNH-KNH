package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.MOD_VERSION
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Isolated workload matrix for storage size, historical reads and reopen cost of the tree. */
internal object BenchmarkStorageSuite {
    data class Scenario(
        val name: String,
        val pattern: BenchmarkGenerator.Pattern,
        val epochs: Int,
    ) {
        init {
            require(name.isNotBlank() && epochs > 0)
        }
    }

    enum class Status {
        PASS,
        STOPPED,
        FAIL,
    }

    data class Result(val file: Path, val status: Status)

    val defaultScenarios =
        listOf(
            Scenario("sparse-32k", BenchmarkGenerator.Pattern.SPARSE, 2_000),
            Scenario("mixed-32k", BenchmarkGenerator.Pattern.MIXED, 2_000),
            Scenario("adversarial-32k", BenchmarkGenerator.Pattern.ADVERSARIAL, 2_000),
            Scenario("adversarial-1m", BenchmarkGenerator.Pattern.ADVERSARIAL, 62_500),
        )

    @Suppress("LongMethod", "NestedBlockDepth", "TooGenericExceptionCaught", "ThrowsCount")
    fun run(
        directory: Path,
        scenarios: List<Scenario> = defaultScenarios,
        wideWorldSide: Int = 0,
        giantWorldSide: Int = 0,
        giantWorldHotEpochs: Int = 50_000,
        shouldStop: () -> Boolean = { false },
        onProgress: (String) -> Unit = {},
    ): Result {
        require(scenarios.isNotEmpty())
        val reports = directory.resolve("reports")
        Files.createDirectories(reports)
        val file = Files.createTempFile(reports, "storage-suite-", ".txt")
        val work = Files.createTempDirectory(reports, "suite-work-")
        var status = Status.PASS
        try {
            Files.newBufferedWriter(file).use { writer ->
                fun log(line: String) {
                    writer.appendLine(line)
                    writer.flush()
                }

                log("Palimpsest storage workload suite (persistent quadtree)")
                log("generated_utc=${Instant.now()}")
                log("mod_version=$MOD_VERSION")
                log("java=${System.getProperty("java.version")}")
                log("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
                log("processors=${Runtime.getRuntime().availableProcessors()}")
                log("max_heap_bytes=${Runtime.getRuntime().maxMemory()}")
                log("read_samples=${BenchmarkReadProbe.SAMPLES} warmup=8 viewport=8x6 at (0,0)")
                log("requires_existing_history=false isolated_fixtures=true")
                try {
                    for (color in TileShapeScenario.run(work.resolve("tile-shapes"))) {
                        log(
                            "case=tile-shapes pattern=${color.name} tiles=${color.tiles} sealed_bytes=${color.sealedBytes} plain_bytes=${color.plainBytes} bytes_per_tile=${color.sealedBytes / color.tiles} linked=${color.linked}"
                        )
                    }
                    log("case_status=PASS case=tile-shapes")
                    for ((scenarioIndex, scenario) in scenarios.withIndex()) {
                        if (shouldStop()) throw Stopped()
                        val caseDirectory = work.resolve("case-$scenarioIndex")
                        onProgress("Storage suite: ${scenario.name} (0/${scenario.epochs} epochs)")
                        log(
                            "case=${scenario.name} pattern=${scenario.pattern} target_epochs=${scenario.epochs}"
                        )
                        val keys = visibleKeys()
                        val latest: Long
                        val middle: Long
                        val middleDigest: String
                        val latestDigest: String
                        BenchmarkWorld(caseDirectory).use { world ->
                            var generated = 0
                            var generatedNanos = 0L
                            var tiles = 0L
                            var nodes = 0L
                            var patched = 0L
                            while (generated < scenario.epochs) {
                                if (shouldStop()) throw Stopped()
                                val batch = minOf(5_000, scenario.epochs - generated)
                                val result =
                                    BenchmarkGenerator.append(world, scenario.pattern, batch)
                                world.tree.sealIfDue()
                                generated += batch
                                generatedNanos += result.elapsedNanos
                                tiles += result.tilesWritten
                                nodes += result.nodesWritten
                                patched += result.nodesPatched
                                onProgress(
                                    "Storage suite: ${scenario.name} ($generated/${scenario.epochs} epochs)"
                                )
                                log(
                                    "progress=${scenario.name} epochs=$generated tiles=$tiles nodes=$nodes ${footprint(caseDirectory)}"
                                )
                            }
                            if (shouldStop()) throw Stopped()
                            log(
                                "generated_nanos=$generatedNanos commits=${world.tree.roots.size} tiles=$tiles nodes=$nodes nodes_patched=$patched ${footprint(caseDirectory)}"
                            )
                            val sealStart = System.nanoTime()
                            world.tree.seal()
                            log(
                                "seal_nanos=${System.nanoTime() - sealStart} ${footprint(caseDirectory)}"
                            )
                            latest = world.latestEpoch
                            middle = latest / 2
                            middleDigest = tileDigest(world, keys, middle)
                            latestDigest = tileDigest(world, keys, latest)
                            log("middle_epoch=$middle middle_pixel_sha256=$middleDigest")
                            log("latest_epoch=$latest latest_pixel_sha256=$latestDigest")
                            measure(::log, "middle", world, middle)
                            measure(::log, "latest", world, latest)
                            val noOp =
                                world.tree.commit(
                                    latest + 1,
                                    keys.associateWith {
                                        checkNotNull(world.tree.tile(it, latest))
                                            .withEpoch(latest + 1)
                                    },
                                )
                            check(noOp.tilesWritten == 0 && noOp.nodesWritten == 0) {
                                "Unchanged observations created storage in ${scenario.name}"
                            }
                            log("noop_tiles_written=${noOp.tilesWritten} noop_bytes=${noOp.bytes}")
                        }
                        val reopenStart = System.nanoTime()
                        BenchmarkWorld(caseDirectory).use { world ->
                            log(
                                "reopen_nanos=${System.nanoTime() - reopenStart} roots=${world.tree.roots.size}"
                            )
                            check(tileDigest(world, keys, latest) == latestDigest) {
                                "Reopening changed latest pixels in ${scenario.name}"
                            }
                            check(tileDigest(world, keys, middle) == middleDigest) {
                                "Reopening changed historical pixels in ${scenario.name}"
                            }
                            measure(::log, "after_reopen_cold", world, latest, warmup = 0)
                            measure(::log, "after_reopen", world, latest)
                            measure(::log, "after_reopen_middle", world, middle)
                        }
                        log("case_status=PASS case=${scenario.name}")
                    }
                    if (wideWorldSide > 0) {
                        if (shouldStop()) throw Stopped()
                        onProgress(
                            "Storage suite: wide world ($wideWorldSide x $wideWorldSide tiles)"
                        )
                        val wide =
                            WideWorldLoadScenario.run(
                                work.resolve("wide-world"),
                                wideWorldSide,
                                shouldStop,
                                onProgress,
                            ) ?: throw Stopped()
                        log(
                            "case=wide-world tiles=${wide.tiles} segment_bytes=${wide.segmentBytes} generate_nanos=${wide.generateNanos}"
                        )
                        for (level in wide.levels) log(
                            "wide_lod=${level.lod} covered_tiles=${level.coveredTiles} nodes_read=${level.nodesRead} tiles_decoded=${level.tilesDecoded} cold_page_nanos=${level.coldPageNanos} warm_page_nanos=${level.warmPageNanos} historical_page_nanos=${level.historicalPageNanos} fresh_open_page_nanos=${level.freshOpenPageNanos}"
                        )
                        log("case_status=PASS case=wide-world")
                    }
                    if (giantWorldSide > 0) {
                        if (shouldStop()) throw Stopped()
                        val passed =
                            GiantWorldScenario.run(
                                work.resolve("giant-world"),
                                giantWorldSide,
                                giantWorldHotEpochs,
                                ::log,
                                shouldStop,
                                onProgress,
                            )
                        if (!passed) throw Stopped()
                    }
                    log("status=PASS")
                } catch (_: Stopped) {
                    status = Status.STOPPED
                    log("status=STOPPED")
                } catch (failure: Exception) {
                    status = Status.FAIL
                    log("status=FAIL")
                    log(failure.stackTraceToString())
                }
            }
        } finally {
            Files.walk(work).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
        return Result(file, status)
    }

    private fun measure(
        log: (String) -> Unit,
        label: String,
        world: BenchmarkWorld,
        epoch: Long,
        warmup: Int = 8,
    ) {
        repeat(warmup) { BenchmarkTileRenderer.read(world, epoch, 0, 0) }
        val read = BenchmarkTileRenderer.read(world, epoch, 0, 0)
        val probe = if (warmup == 0) null else BenchmarkReadProbe.run(world, epoch, 0, 0)
        log(
            "$label epoch=$epoch " +
                (probe?.let {
                    "median_us=${it.medianMicros} p95_us=${it.p95Micros} max_us=${it.maxMicros} "
                } ?: "single_us=${read.elapsedNanos / 1_000} ") +
                "nodes_read=${read.nodesRead} tiles_decoded=${read.tilesDecoded}"
        )
    }

    private fun visibleKeys(): List<TileKey> = buildList {
        for (z in 0 until BenchmarkTileRenderer.VIEW_ROWS) for (x in
            0 until BenchmarkTileRenderer.VIEW_COLUMNS) add(TileKey(x, z))
    }

    private fun tileDigest(world: BenchmarkWorld, keys: List<TileKey>, epoch: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (key in keys) {
            val tile = checkNotNull(world.tree.tile(key, epoch))
            for (position in 0 until TileRecord.PIXELS) digest.update(tile.block(position).toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun footprint(directory: Path): String {
        var sealed = 0L
        var sealedBytes = 0L
        var activeBytes = 0L
        Files.walk(directory).use { paths ->
            paths.filter(Files::isRegularFile).forEach { file ->
                val name = file.fileName.toString()
                if (!name.endsWith(".pseg")) return@forEach
                if (name.startsWith("active-")) activeBytes += Files.size(file)
                else {
                    sealed++
                    sealedBytes += Files.size(file)
                }
            }
        }
        return "sealed_bytes=$sealedBytes active_bytes=$activeBytes segments=$sealed"
    }

    private class Stopped : RuntimeException()
}
