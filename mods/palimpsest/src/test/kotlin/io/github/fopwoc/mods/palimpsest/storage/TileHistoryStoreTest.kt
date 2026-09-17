package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TileHistoryStoreTest {
  @Test
  fun historyReopensAndSkipsOverwrittenLayers() = withStore { directory ->
    val key = TileKey(3, 5)
    val initial = ByteArray(TileLayer.PIXELS) { it.toByte() }
    val first = initial.copyOf().apply { this[12] = 42 }
    val second = first.copyOf().apply { this[12] = 81 }
    TileHistoryStore(directory).use { store ->
      store.append(
          listOf(
              TileLayer.complete(key, 0, initial),
              assertNotNull(TileLayer.changed(key, 5, initial, first)),
          )
      )
      store.append(listOf(assertNotNull(TileLayer.changed(key, 10, first, second))))
      assertNull(store.read(key, -1))
      assertContentEquals(initial, assertNotNull(store.read(key, 4)).colors)
      assertContentEquals(first, assertNotNull(store.read(key, 5)).colors)
      val latest = assertNotNull(store.read(key, 10))
      assertContentEquals(second, latest.colors)
      assertEquals(3, latest.layersVisited)
      assertEquals(2, latest.layersDecoded)
    }
    TileHistoryStore(directory).use { reopened ->
      assertEquals(1, reopened.tileCount)
      assertEquals(3, reopened.layerCount)
      assertEquals(10, reopened.latestEpoch)
      assertContentEquals(second, assertNotNull(reopened.read(key, 10)).colors)
    }
  }

  @Test
  fun rejectsCorruptedSegment() = withStore { directory ->
    TileHistoryStore(directory).use { store ->
      store.append(listOf(TileLayer.complete(TileKey(0, 0), 0, ByteArray(TileLayer.PIXELS))))
    }
    Files.list(directory).use { files ->
      val segment =
          files.filter { it.fileName.toString().endsWith(".pseg") }.findFirst().orElseThrow()
      val bytes = Files.readAllBytes(segment)
      bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
      Files.write(segment, bytes)
    }
    assertFailsWith<IOException> { TileHistoryStore(directory) }
  }

  private inline fun withStore(test: (java.nio.file.Path) -> Unit) {
    val directory = Files.createTempDirectory("palimpsest-test-")
    try {
      test(directory)
    } finally {
      Files.walk(directory).use { files ->
        files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
