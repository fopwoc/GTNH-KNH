package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.MOD_VERSION
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Isolated workload matrix for storage size, historical reads, checkpoints, and index rebuilds. */
internal object BenchmarkStorageSuite {
  data class Scenario(val name: String, val pattern: BenchmarkGenerator.Pattern, val epochs: Int) {
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

  fun run(
      directory: Path,
      scenarios: List<Scenario> = defaultScenarios,
      wideWorldSide: Int = 0,
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

        log("Palimpsest storage workload suite")
        log("generated_utc=${Instant.now()}")
        log("mod_version=$MOD_VERSION")
        log("java=${System.getProperty("java.version")}")
        log("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        log("processors=${Runtime.getRuntime().availableProcessors()}")
        log("max_heap_bytes=${Runtime.getRuntime().maxMemory()}")
        log("read_samples=${BenchmarkReadProbe.SAMPLES} warmup=8 viewport=8x6 at (0,0)")
        log("requires_existing_history=false isolated_fixtures=true")
        try {
          val structured = StructuredColorScenario.run(work.resolve("structured-colors"))
          log(
              "case=structured-colors flat_bytes=${structured.flatBytes} flat_baseline_bytes=${structured.flatBaselineBytes} varied_bytes=${structured.variedBytes} varied_baseline_bytes=${structured.variedBaselineBytes} patch_bytes=${structured.patchBytes} patch_baseline_bytes=${structured.patchBaselineBytes}"
          )
          log("case_status=PASS case=structured-colors")
          val colorCases =
              ColorDistributionScenario.run(work.resolve("color-distributions"), shouldStop)
                  ?: throw Stopped()
          for (color in colorCases) {
            log(
                "case=color-distribution pattern=${color.name} tiles=${color.tiles} layers=${color.layers} sealed_bytes=${color.sealedBytes} plain_bytes=${color.plainBytes} sampled_record_bytes=${color.sampledBytes} sample_nanos=${color.sampleNanos}"
            )
          }
          log("case_status=PASS case=color-distribution")
          for ((scenarioIndex, scenario) in scenarios.withIndex()) {
            if (shouldStop()) throw Stopped()
            val caseDirectory = work.resolve("case-$scenarioIndex")
            onProgress("Storage suite: ${scenario.name} (0/${scenario.epochs} epochs)")
            log(
                "case=${scenario.name} pattern=${scenario.pattern} target_epochs=${scenario.epochs}"
            )
            TileHistoryStore(caseDirectory).use { store ->
              var generated = 0
              var generatedNanos = 0L
              while (generated < scenario.epochs) {
                if (shouldStop()) throw Stopped()
                val batch = minOf(5_000, scenario.epochs - generated)
                val result = BenchmarkGenerator.append(store, scenario.pattern, batch)
                generated += batch
                generatedNanos += result.elapsedNanos
                onProgress("Storage suite: ${scenario.name} ($generated/${scenario.epochs} epochs)")
                log(
                    "progress=${scenario.name} epochs=$generated layers=${store.layerCount} sealed_bytes=${store.byteCount}"
                )
              }
              if (shouldStop()) throw Stopped()
              log(
                  "generated_nanos=$generatedNanos layers=${store.layerCount} sealed_bytes=${store.byteCount} append_index_array_bytes=${store.indexArrayBytes}"
              )
              val reopenStart = System.nanoTime()
              store.reload()
              log(
                  "initial_reopen_nanos=${System.nanoTime() - reopenStart} reopened_index_array_bytes=${store.indexArrayBytes}"
              )
              val latest = store.latestEpoch
              val middle = latest / 2
              val keys = visibleKeys()
              val middleDigest = tileDigest(store, keys, middle)
              val latestDigest = tileDigest(store, keys, latest)
              log("middle_epoch=$middle middle_pixel_sha256=$middleDigest")
              log("latest_epoch=$latest latest_pixel_sha256=$latestDigest")
              measure(::log, "middle", store, middle)
              measure(::log, "before_checkpoint", store, latest)
              val noOp =
                  store.append(
                      keys.map { key ->
                        TileLayer.full(
                            key,
                            latest + 1,
                            checkNotNull(store.read(key, latest)).colors,
                        )
                      }
                  )
              check(
                  noOp.layersWritten == 0 &&
                      noOp.layersDiscarded == keys.size &&
                      noOp.bytesAdded == 0L
              ) {
                "Unchanged observations created storage in ${scenario.name}"
              }
              log(
                  "noop_layers_discarded=${noOp.layersDiscarded} noop_bytes_added=${noOp.bytesAdded}"
              )
              if (shouldStop()) throw Stopped()
              val checkpoint = BenchmarkGenerator.checkpoint(store, keys)
              val checkpointEpoch = store.latestEpoch
              check(tileDigest(store, keys, checkpointEpoch) == latestDigest) {
                "Checkpoint changed latest pixels in ${scenario.name}"
              }
              log(
                  "checkpoint_epoch=$checkpointEpoch checkpoint_layers=${checkpoint.layersWritten} checkpoint_bytes=${checkpoint.bytesAdded} checkpoint_nanos=${checkpoint.elapsedNanos}"
              )
              measure(::log, "after_checkpoint", store, checkpointEpoch)
              val finalReopenStart = System.nanoTime()
              store.reload()
              log(
                  "final_reopen_nanos=${System.nanoTime() - finalReopenStart} final_index_array_bytes=${store.indexArrayBytes}"
              )
              check(tileDigest(store, keys, checkpointEpoch) == latestDigest) {
                "Reopening changed latest pixels in ${scenario.name}"
              }
              check(tileDigest(store, keys, middle) == middleDigest) {
                "Checkpoint or reopen changed historical pixels in ${scenario.name}"
              }
              measure(::log, "after_reopen", store, checkpointEpoch)
              log("case_status=PASS case=${scenario.name}")
            }
          }
          if (wideWorldSide > 0) {
            if (shouldStop()) throw Stopped()
            onProgress("Storage suite: wide world ($wideWorldSide x $wideWorldSide tiles)")
            val wide =
                WideWorldLoadScenario.run(
                    work.resolve("wide-world"),
                    wideWorldSide,
                    shouldStop,
                    onProgress,
                ) ?: throw Stopped()
            log(
                "case=wide-world tiles=${wide.tiles} segment_bytes=${wide.segmentBytes} index_cache_bytes=${wide.indexCacheBytes}"
            )
            for (level in wide.levels) log(
                "wide_cache_limit=${level.cacheLimit} disk_index=${level.indexCacheEnabled} wide_lod=${level.lod} covered_tiles=${level.coveredTiles} tile_lookups=${level.tileLookups} present_samples=${level.presentSamples} cold_page_nanos=${level.coldPageNanos} warm_page_nanos=${level.warmPageNanos} historical_page_nanos=${level.historicalPageNanos} logical_record_bytes_read=${level.logicalRecordBytes} open_regions=${level.openRegions} region_opens=${level.regionOpens} region_evictions=${level.regionEvictions} open_index_array_bytes=${level.openIndexArrayBytes} index_cache_hits=${level.indexCacheHits}"
            )
            log("case_status=PASS case=wide-world")
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
      store: TileHistoryStore,
      epoch: Long,
  ) {
    repeat(8) { BenchmarkTileRenderer.read(store, epoch, 0, 0) }
    val probe = BenchmarkReadProbe.run(store, epoch, 0, 0)
    val read = BenchmarkTileRenderer.read(store, epoch, 0, 0)
    log(
        "$label epoch=$epoch median_us=${probe.medianMicros} p95_us=${probe.p95Micros} max_us=${probe.maxMicros} visited=${read.visitedLayers} decoded=${read.decodedLayers} skipped=${read.skippedLayers}"
    )
  }

  private fun visibleKeys(): List<TileKey> = buildList {
    for (z in 0 until BenchmarkTileRenderer.VIEW_ROWS) {
      for (x in 0 until BenchmarkTileRenderer.VIEW_COLUMNS) add(TileKey(x, z))
    }
  }

  private fun tileDigest(store: TileHistoryStore, keys: List<TileKey>, epoch: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (key in keys) digest.update(checkNotNull(store.read(key, epoch)).colors)
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private class Stopped : RuntimeException()
}
