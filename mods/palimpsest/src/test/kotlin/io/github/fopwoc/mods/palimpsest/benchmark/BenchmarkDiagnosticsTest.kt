package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkDiagnosticsTest {
  @Test
  fun reportMeasuresTheSameViewportAcrossCheckpointAndReopen() {
    val directory = Files.createTempDirectory("palimpsest-diagnostics-")
    try {
      TileHistoryStore(directory).use { store ->
        BenchmarkGenerator.append(store)
        val result = BenchmarkDiagnostics.run(store, directory, 0, 0)
        val report = Files.readString(result.file)
        assertTrue(result.successful, report)
        assertTrue(report.contains("before_epoch=250"))
        assertTrue(report.contains("checkpoint_epoch=251 checkpoint_layers=48"))
        assertTrue(report.contains("before: epoch=250 median_us="))
        assertTrue(report.contains("after: epoch=251 median_us="))
        assertTrue(report.contains("reopened: epoch=251 median_us="))
        assertTrue(report.contains("status=PASS"))
        assertEquals(251, store.latestEpoch)
      }
    } finally {
      Files.walk(directory).use { files ->
        files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
