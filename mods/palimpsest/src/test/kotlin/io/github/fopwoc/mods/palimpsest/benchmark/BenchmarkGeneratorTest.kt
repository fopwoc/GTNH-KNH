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
        assertEquals(250, store.latestEpoch)
        assertEquals(1024, store.tileCount)
        assertTrue(first.bytesAdded > 0)
        assertNotNull(store.read(TileKey(0, 0), 0))
      }
      TileHistoryStore(directory).use { store ->
        val second = BenchmarkGenerator.append(store)
        assertEquals(250 * 16, second.layersWritten)
        assertEquals(500, store.latestEpoch)
        assertNotNull(store.read(TileKey(31, 31), 500))
      }
    } finally {
      Files.walk(directory).use { files ->
        files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
