package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.file.Files
import java.util.Random
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

  @Test
  fun randomizedLayersAndCheckpointsMatchFullSnapshots() = withStore { directory ->
    val random = Random(0x50414C49)
    val keys = List(9) { TileKey(it % 3, it / 3) }
    val current =
        keys
            .associateWith { ByteArray(TileLayer.PIXELS) { random.nextInt(256).toByte() } }
            .toMutableMap()
    val snapshots = ArrayList<Map<TileKey, ByteArray>>()
    val pending = ArrayList<TileLayer>()
    pending += keys.map { TileLayer.complete(it, 0, checkNotNull(current[it])) }
    snapshots += current.mapValues { it.value.copyOf() }
    TileHistoryStore(directory).use { store ->
      for (epoch in 1L..300L) {
        if (epoch % 73L == 0L) {
          pending += keys.map { TileLayer.complete(it, epoch, checkNotNull(current[it])) }
        } else {
          repeat(1 + random.nextInt(3)) {
            val key = keys[random.nextInt(keys.size)]
            if (pending.any { it.key == key && it.epoch == epoch }) return@repeat
            val old = checkNotNull(current[key])
            val next = old.copyOf()
            val count = if (random.nextInt(20) == 0) TileLayer.PIXELS else 1 + random.nextInt(100)
            repeat(count) {
              val position = random.nextInt(TileLayer.PIXELS)
              next[position] = ((next[position].toInt() and 255) + 1 + random.nextInt(255)).toByte()
            }
            pending += assertNotNull(TileLayer.changed(key, epoch, old, next))
            current[key] = next
          }
        }
        snapshots += current.mapValues { it.value.copyOf() }
        if (epoch % 37L == 0L) {
          store.append(pending.toList())
          pending.clear()
        }
      }
      store.append(pending)
      verifySnapshots(store, keys, snapshots)
    }
    TileHistoryStore(directory).use { reopened -> verifySnapshots(reopened, keys, snapshots) }
  }

  private fun verifySnapshots(
      store: TileHistoryStore,
      keys: List<TileKey>,
      snapshots: List<Map<TileKey, ByteArray>>,
  ) {
    val random = Random(0x43484543)
    repeat(300) {
      val epoch = random.nextInt(snapshots.size)
      val key = keys[random.nextInt(keys.size)]
      assertContentEquals(
          snapshots[epoch][key],
          assertNotNull(store.read(key, epoch.toLong())).colors,
      )
    }
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
