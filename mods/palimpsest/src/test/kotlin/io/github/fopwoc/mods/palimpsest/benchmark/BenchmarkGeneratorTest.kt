package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchmarkGeneratorTest {
  @Test
  fun generatedHistorySurvivesReopenAndAnotherBatch() {
    val directory = Files.createTempDirectory("palimpsest-benchmark-")
    try {
      TileHistoryStore(directory).use { store ->
        val first = BenchmarkGenerator.append(store)
        assertEquals(1024 + 250 * 16, first.layersWritten)
        assertEquals(1024 * 256 + 250 * 16 * 8, first.coveredCells)
        assertEquals(250, store.latestEpoch)
        assertEquals(1024, store.tileCount)
        assertTrue(first.bytesAdded > 0)
        assertNotNull(store.read(TileKey(0, 0), 0))
      }
      TileHistoryStore(directory).use { store ->
        val second = BenchmarkGenerator.append(store, BenchmarkGenerator.Pattern.MIXED)
        assertEquals(250 * 16, second.layersWritten)
        assertTrue(second.coveredCells > 250 * 16 * 8)
        assertEquals(500, store.latestEpoch)
        val key = TileKey(31, 31)
        val before = assertNotNull(store.read(key, 500)).colors
        val checkpoint = BenchmarkGenerator.checkpoint(store)
        assertEquals(1024, checkpoint.layersWritten)
        assertEquals(1024 * 256, checkpoint.coveredCells)
        assertEquals(501, store.latestEpoch)
        val after = assertNotNull(store.read(key, 501))
        assertEquals(1, after.layersVisited)
        assertEquals(1, after.layersDecoded)
        assertTrue(before.contentEquals(after.colors))
      }
      TileHistoryStore(directory).use { reopened ->
        assertEquals(501, reopened.latestEpoch)
        assertEquals(1, assertNotNull(reopened.read(TileKey(31, 31), 501)).layersDecoded)
        assertNotNull(reopened.read(TileKey(31, 31), 500))
      }
    } finally {
      Files.walk(directory).use { files ->
        files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
