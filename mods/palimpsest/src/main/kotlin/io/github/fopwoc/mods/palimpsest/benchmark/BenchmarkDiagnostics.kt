package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant

/** Writes a repeatable before/after checkpoint read benchmark for one viewport. */
internal object BenchmarkDiagnostics {
    data class Result(val file: Path, val successful: Boolean)

    @Suppress("TooGenericExceptionCaught")
    fun run(store: TileHistoryStore, directory: Path, left: Int, top: Int): Result {
        val reports = directory.resolve("reports")
        Files.createDirectories(reports)
        val file = Files.createTempFile(reports, "diagnostic-", ".txt")
        val report = StringBuilder()
        report.appendLine("Palimpsest storage diagnostics")
        report.appendLine("generated_utc=${Instant.now()}")
        report.appendLine("java=${System.getProperty("java.version")}")
        report.appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        report.appendLine("processors=${Runtime.getRuntime().availableProcessors()}")
        report.appendLine(
            "viewport=($left,$top) size=${BenchmarkTileRenderer.VIEW_COLUMNS}x${BenchmarkTileRenderer.VIEW_ROWS}"
        )
        report.appendLine("samples=${BenchmarkReadProbe.SAMPLES} warmup=8")
        val successful =
            try {
                val beforeEpoch = store.latestEpoch
                require(store.tileCount > 0) { "Generate benchmark history first" }
                val visible = BenchmarkTileRenderer.read(store, beforeEpoch, left, top)
                val keys = visible.tileCosts.map { it.key }
                require(keys.isNotEmpty()) { "No stored tiles in the selected viewport" }
                report.appendLine("visible_tiles=${keys.size}")
                report.appendLine("before_epoch=$beforeEpoch")
                report.appendLine(
                    "before_tiles=${store.tileCount} before_layers=${store.layerCount} before_sealed_bytes=${store.byteCount} before_index_array_bytes=${store.indexArrayBytes}"
                )
                val expected = tileDigest(store, keys, beforeEpoch)
                report.appendLine("pixel_sha256=$expected")
                measure(report, "before", store, beforeEpoch, left, top)

                val checkpoint = BenchmarkGenerator.checkpoint(store, keys)
                val afterEpoch = store.latestEpoch
                report.appendLine(
                    "checkpoint_epoch=$afterEpoch checkpoint_layers=${checkpoint.layersWritten} checkpoint_bytes=${checkpoint.bytesAdded} checkpoint_nanos=${checkpoint.elapsedNanos}"
                )
                check(tileDigest(store, keys, afterEpoch) == expected) {
                    "Checkpoint changed visible pixels"
                }
                report.appendLine(
                    "after_tiles=${store.tileCount} after_layers=${store.layerCount} after_sealed_bytes=${store.byteCount} after_index_array_bytes=${store.indexArrayBytes}"
                )
                measure(report, "after", store, afterEpoch, left, top)

                val reopenStart = System.nanoTime()
                store.reload()
                report.appendLine("reopen_nanos=${System.nanoTime() - reopenStart}")
                check(tileDigest(store, keys, afterEpoch) == expected) {
                    "Reopening changed visible pixels"
                }
                report.appendLine(
                    "reopened_tiles=${store.tileCount} reopened_layers=${store.layerCount} reopened_sealed_bytes=${store.byteCount} reopened_index_array_bytes=${store.indexArrayBytes}"
                )
                measure(report, "reopened", store, afterEpoch, left, top)
                report.appendLine("status=PASS")
                true
            } catch (failure: Exception) {
                report.appendLine("status=FAIL")
                report.appendLine(failure.stackTraceToString())
                false
            }
        Files.writeString(file, report, StandardOpenOption.TRUNCATE_EXISTING)
        return Result(file, successful)
    }

    private fun measure(
        report: StringBuilder,
        label: String,
        store: TileHistoryStore,
        epoch: Long,
        left: Int,
        top: Int,
    ) {
        repeat(8) { BenchmarkTileRenderer.read(store, epoch, left, top) }
        val probe = BenchmarkReadProbe.run(store, epoch, left, top)
        val read = BenchmarkTileRenderer.read(store, epoch, left, top)
        report.appendLine(
            "$label: epoch=$epoch median_us=${probe.medianMicros} p95_us=${probe.p95Micros} max_us=${probe.maxMicros} visible_tiles=${read.visibleTiles} visited_layers=${read.visitedLayers} decoded_layers=${read.decodedLayers} skipped_layers=${read.skippedLayers}"
        )
    }

    private fun tileDigest(store: TileHistoryStore, keys: List<TileKey>, epoch: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (key in keys) digest.update(checkNotNull(store.read(key, epoch)).colors)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
