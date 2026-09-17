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
  fun groupedSparseHistoryUsesFarLessDiskThanFixedMasks() = withStore { directory ->
    val key = TileKey(0, 0)
    val layers = ArrayList<TileLayer>()
    var current = ByteArray(TileLayer.PIXELS)
    layers += TileLayer.full(key, 0, current)
    for (epoch in 1L..1000L) {
      val next = current.copyOf().apply { this[37] = (this[37] + 1).toByte() }
      layers += assertNotNull(TileLayer.changed(key, epoch, current, next))
      current = next
    }
    TileHistoryStore(directory).use { store ->
      val result = store.append(layers)
      assertEquals(1001, result.layersWritten)
      assertEquals(5_282, result.bytesAdded)
      assertContentEquals(current, assertNotNull(store.read(key, 1000)).colors)
    }
    TileHistoryStore(directory).use { reopened ->
      assertEquals(1001, reopened.layerCount)
      assertContentEquals(current, assertNotNull(reopened.read(key, 1000)).colors)
    }
  }

  @Test
  fun appendTrimsUnchangedPixelsAndOmitsNoOpSegmentsButKeepsSnapshots() = withStore { directory ->
    val key = TileKey(2, 4)
    val initial = ByteArray(TileLayer.PIXELS)
    val expected = initial.copyOf().apply { this[2] = 7 }
    val mask = longArrayOf((1L shl 1) or (1L shl 2), 0, 0, 0)
    TileHistoryStore(directory).use { store ->
      store.append(listOf(TileLayer.full(key, 0, initial)))
      val changed = TileLayer(key, 1, mask, byteArrayOf(0, 7))
      val repeated = TileLayer(key, 2, longArrayOf(1L shl 2, 0, 0, 0), byteArrayOf(7))
      val written = store.append(listOf(changed, repeated))
      assertEquals(1, written.layersWritten)
      assertEquals(1, written.layersDiscarded)
      assertEquals(1, written.coveredCells)
      assertEquals(1, store.latestEpoch)
      assertContentEquals(expected, assertNotNull(store.read(key, 2)).colors)

      val bytes = store.byteCount
      val files = Files.list(directory).use { it.count() }
      val noOp = store.append(listOf(TileLayer(key, 3, mask, byteArrayOf(0, 7))))
      assertEquals(0, noOp.layersWritten)
      assertEquals(1, noOp.layersDiscarded)
      assertEquals(0, noOp.bytesAdded)
      assertEquals(bytes, store.byteCount)
      assertEquals(files, Files.list(directory).use { it.count() })

      val snapshot = store.append(listOf(TileLayer.snapshot(key, 4, expected)))
      assertEquals(1, snapshot.layersWritten)
      assertEquals(256, snapshot.coveredCells)
      assertEquals(4, store.latestEpoch)
      assertEquals(3, store.layerCount)
      val unchangedFull = store.append(listOf(TileLayer.full(key, 5, expected)))
      assertEquals(0, unchangedFull.layersWritten)
      assertEquals(0, unchangedFull.bytesAdded)
    }
    TileHistoryStore(directory).use { reopened ->
      assertContentEquals(initial, assertNotNull(reopened.read(key, 0)).colors)
      assertEquals(1, assertNotNull(reopened.read(key, 4)).layersDecoded)
      assertEquals(3, reopened.layerCount)
      assertEquals(0, reopened.append(listOf(TileLayer.full(key, 6, expected))).layersWritten)
    }
  }

  @Test
  fun firstLayerMustCoverTheWholeTile() = withStore { directory ->
    TileHistoryStore(directory).use { store ->
      assertFailsWith<IllegalArgumentException> {
        store.append(listOf(TileLayer(TileKey(0, 0), 0, longArrayOf(1, 0, 0, 0), byteArrayOf(1))))
      }
      assertEquals(0, store.layerCount)
    }
  }

  @Test
  fun historyReopensAndSkipsOverwrittenLayers() = withStore { directory ->
    val key = TileKey(3, 5)
    val initial = ByteArray(TileLayer.PIXELS) { it.toByte() }
    val first = initial.copyOf().apply { this[12] = 42 }
    val second = first.copyOf().apply { this[12] = 81 }
    TileHistoryStore(directory).use { store ->
      store.append(
          listOf(
              TileLayer.full(key, 0, initial),
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
      store.append(listOf(TileLayer.full(TileKey(0, 0), 0, ByteArray(TileLayer.PIXELS))))
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
    pending += keys.map { TileLayer.full(it, 0, checkNotNull(current[it])) }
    snapshots += current.mapValues { it.value.copyOf() }
    TileHistoryStore(directory).use { store ->
      for (epoch in 1L..300L) {
        if (epoch % 73L == 0L) {
          pending += keys.map { TileLayer.snapshot(it, epoch, checkNotNull(current[it])) }
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

  @Test
  fun coverageGroupsSkipRepeatedChangesToAnAlreadyResolvedPixel() = withStore { directory ->
    val key = TileKey(1, 1)
    val initial = ByteArray(TileLayer.PIXELS)
    val layers = ArrayList<TileLayer>()
    layers += TileLayer.full(key, 0, initial)
    var current = initial
    for (epoch in 1L..130L) {
      val next = current.copyOf().apply { this[7] = (this[7] + 1).toByte() }
      layers += assertNotNull(TileLayer.changed(key, epoch, current, next))
      current = next
    }
    TileHistoryStore(directory).use { store ->
      store.append(layers)
      val read = assertNotNull(store.read(key, 130))
      assertContentEquals(current, read.colors)
      assertEquals(64, read.layersSkipped)
      assertEquals(2, read.layersDecoded)
    }
    TileHistoryStore(directory).use { reopened ->
      val read = assertNotNull(reopened.read(key, 130))
      assertContentEquals(current, read.colors)
      assertEquals(64, read.layersSkipped)
    }
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
