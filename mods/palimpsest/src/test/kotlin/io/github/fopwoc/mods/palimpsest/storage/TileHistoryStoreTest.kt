package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.file.Files
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileHistoryStoreTest {
    @Test
    fun disposableIndexLoadsAndRecoversFromStaleOrDamagedCache() = withStore { directory ->
        val keys = List(300) { TileKey(it, 0) }
        TileHistoryStore(directory).use { store ->
            store.append(
                keys.map {
                    TileLayer.full(it, 0, ByteArray(TileLayer.PIXELS) { cell -> cell.toByte() })
                }
            )
        }
        val cache = directory.resolve(".index-cache.pidx")
        assertTrue(Files.isRegularFile(cache))
        TileHistoryStore(directory).use { store ->
            assertTrue(store.loadedFromIndexCache)
            assertEquals(136, store.readPixel(keys[12], 0, 136))
            store.append(
                listOf(TileLayer.full(TileKey(400, 0), 1, ByteArray(TileLayer.PIXELS) { 8 }))
            )
        }
        TileHistoryStore(directory).use { store ->
            assertTrue(store.loadedFromIndexCache)
            assertEquals(8, store.readPixel(TileKey(400, 0), 1, 136))
        }
        TileHistoryStore(directory, indexCacheEnabled = false).use { store ->
            store.append(
                listOf(TileLayer.full(TileKey(500, 0), 1, ByteArray(TileLayer.PIXELS) { 9 }))
            )
        }
        TileHistoryStore(directory).use { store ->
            assertFalse(store.loadedFromIndexCache)
            assertEquals(9, store.readPixel(TileKey(500, 0), 1, 136))
        }
        TileHistoryStore(directory).use { assertTrue(it.loadedFromIndexCache) }
        Files.write(cache, byteArrayOf(1, 2, 3))
        TileHistoryStore(directory).use { store ->
            assertFalse(store.loadedFromIndexCache)
            assertEquals(136, store.readPixel(keys[12], 1, 136))
        }
        TileHistoryStore(directory).use { assertTrue(it.loadedFromIndexCache) }
    }

    @Test
    fun segmentsAreHashedOncePerProcessUntilTheyChange() = withStore { directory ->
        val key = TileKey(0, 0)
        TileHistoryStore(directory).use { store ->
            store.append(listOf(TileLayer.full(key, 0, ByteArray(TileLayer.PIXELS) { 1 })))
            store.seal()
            store.append(listOf(TileLayer.full(key, 1, ByteArray(TileLayer.PIXELS) { 2 })))
        }
        TileHistoryStore(directory).use { store ->
            assertEquals(2, store.segmentCount)
            assertEquals(2, store.segmentsHashed)
        }
        TileHistoryStore(directory).use { store ->
            assertEquals(0, store.segmentsHashed)
            assertEquals(2, store.readPixel(key, 1, 0))
        }
        TileHistoryStore(directory, indexCacheEnabled = false).use { store ->
            assertEquals(0, store.segmentsHashed)
            assertEquals(2, store.layerCount)
        }
    }

    @Test
    fun sidecarTilesLoadOnDemandAndCleanOnesAreEvicted() = withStore { directory ->
        val keys = List(300) { TileKey(it, 0) }
        val initial = ByteArray(TileLayer.PIXELS) { it.toByte() }
        TileHistoryStore(directory).use { store ->
            store.append(keys.map { TileLayer.full(it, 0, initial) })
            store.append(keys.map { TileLayer.snapshot(it, 1, initial) })
        }
        TileHistoryStore(directory, residentIndexBytes = 2_000).use { store ->
            assertTrue(store.loadedFromIndexCache)
            assertEquals(0, store.residentTiles)
            assertEquals(300, store.tileCount)
            assertEquals(600, store.layerCount)
            assertEquals(1, store.latestEpoch)
            assertEquals(12, store.readPixel(keys[0], 1, 12))
            assertEquals(1, store.residentTiles)
            for (key in keys) assertEquals(12, store.readPixel(key, 1, 12))
            assertTrue(store.residentTiles < keys.size, "resident=${store.residentTiles}")
            assertTrue(store.indexArrayBytes <= 2_000 + 200)

            val changed = initial.copyOf().apply { this[12] = 99 }
            store.append(listOf(assertNotNull(TileLayer.changed(keys[7], 2, initial, changed))))
            assertEquals(99, store.readPixel(keys[7], 2, 12))
            assertEquals(12, store.readPixel(keys[7], 1, 12))
            assertEquals(601, store.layerCount)
        }
        TileHistoryStore(directory).use { store ->
            assertTrue(store.loadedFromIndexCache)
            assertEquals(601, store.layerCount)
            assertEquals(99, store.readPixel(keys[7], 2, 12))
            assertEquals(12, store.readPixel(keys[8], 2, 12))
        }
    }

    @Test
    fun pendingLogIsReadableReplayedAfterCrashAndSealedOnClose() = withStore { directory ->
        val key = TileKey(1, 1)
        val initial = ByteArray(TileLayer.PIXELS) { 3 }
        val changed = initial.copyOf().apply { this[5] = 9 }
        val crashed = TileHistoryStore(directory)
        crashed.append(listOf(TileLayer.full(key, 10, initial)))
        crashed.append(listOf(assertNotNull(TileLayer.changed(key, 20, initial, changed))))
        assertEquals(0, crashed.segmentCount)
        assertEquals(2, crashed.walLayers)
        assertEquals(9, crashed.readPixel(key, 20, 5))
        assertEquals(3, crashed.readPixel(key, 10, 5))
        assertTrue(Files.isRegularFile(directory.resolve(TileHistoryStore.LOG_A)))

        TileHistoryStore(directory).use { replayed ->
            assertEquals(0, replayed.segmentCount)
            assertEquals(2, replayed.walLayers)
            assertEquals(2, replayed.layerCount)
            assertEquals(20, replayed.latestEpoch)
            assertEquals(9, replayed.readPixel(key, 20, 5))
            replayed.append(listOf(TileLayer.snapshot(key, 30, changed)))
        }
        TileHistoryStore(directory).use { sealed ->
            assertEquals(1, sealed.segmentCount)
            assertEquals(0, sealed.walLayers)
            assertEquals(3, sealed.layerCount)
            assertEquals(0L, sealed.walBytes)
            assertEquals(9, sealed.readPixel(key, 30, 5))
            assertEquals(3, sealed.readPixel(key, 10, 5))
            assertContentEquals(changed, assertNotNull(sealed.read(key, 25)).colors)
        }
        // The crashed instance is abandoned on purpose: a store directory has one writer.
    }

    @Test
    fun compactionMergesSmallSegmentsAndMergedDuplicatesDedupeDeterministically() =
        withStore { directory ->
            val keys = List(4) { TileKey(it, 0) }
            var current = keys.associateWith { ByteArray(TileLayer.PIXELS) }
            val before = Files.createTempDirectory("palimpsest-peer-")
            try {
                TileHistoryStore(directory, compactFanIn = 8).use { store ->
                    for (epoch in 0L until 8L) {
                        val next = current.mapValues { (key, colors) ->
                            colors.copyOf().apply { this[key.x + epoch.toInt()] = 7 }
                        }
                        store.append(
                            keys.map { key ->
                                if (epoch == 0L) TileLayer.full(key, 0, next.getValue(key))
                                else
                                    assertNotNull(
                                        TileLayer.changed(
                                            key,
                                            epoch,
                                            current.getValue(key),
                                            next.getValue(key),
                                        )
                                    )
                            }
                        )
                        store.seal()
                        current = next
                    }
                    assertEquals(8, store.segmentCount)
                    Files.list(directory).use { files ->
                        files
                            .filter { it.fileName.toString().endsWith(".pseg") }
                            .forEach { Files.copy(it, before.resolve(it.fileName.toString())) }
                    }
                    assertTrue(store.compact())
                    assertEquals(1, store.segmentCount)
                    assertEquals(32, store.layerCount)
                    for (key in keys) {
                        assertContentEquals(
                            current.getValue(key),
                            assertNotNull(store.read(key, 7)).colors,
                        )
                        assertEquals(7, store.readPixel(key, 3, key.x + 3))
                        assertEquals(0, store.readPixel(key, 2, key.x + 3))
                    }
                    assertFalse(store.compact())
                }
                // A peer that synced the small segments and later the merged one holds both.
                Files.list(directory).use { files ->
                    files
                        .filter { it.fileName.toString().endsWith(".pseg") }
                        .forEach { Files.copy(it, before.resolve(it.fileName.toString())) }
                }
                TileHistoryStore(before).use { peer ->
                    assertEquals(9, peer.segmentCount)
                    assertEquals(32, peer.layerCount)
                    for (key in keys) {
                        assertContentEquals(
                            current.getValue(key),
                            assertNotNull(peer.read(key, 7)).colors,
                        )
                        assertEquals(0, peer.readPixel(key, 2, key.x + 3))
                    }
                }
            } finally {
                Files.walk(before).use { files ->
                    files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
                }
            }
        }

    @Test
    fun cachedIndexStillRejectsCorruptedSegment() = withStore { directory ->
        TileHistoryStore(directory).use { store ->
            store.append(
                List(300) {
                    TileLayer.full(
                        TileKey(it, 0),
                        0,
                        ByteArray(TileLayer.PIXELS) { cell -> cell.toByte() },
                    )
                }
            )
        }
        assertTrue(Files.isRegularFile(directory.resolve(".index-cache.pidx")))
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
    fun packedIndexPreservesSparseAndMaskedHistory() = withStore { directory ->
        val keys = List(300) { TileKey(it, 0) }
        val initial = ByteArray(TileLayer.PIXELS) { it.toByte() }
        val sparse = initial.copyOf().apply { this[7] = 9 }
        val masked = sparse.copyOf().apply { for (position in 80 until 120) this[position] = 11 }
        TileHistoryStore(directory).use { store ->
            store.append(keys.map { TileLayer.full(it, 0, initial) })
            store.append(keys.map { assertNotNull(TileLayer.changed(it, 1, initial, sparse)) })
            store.append(keys.map { assertNotNull(TileLayer.changed(it, 2, sparse, masked)) })
        }
        val cache = directory.resolve(".index-cache.pidx")
        assertTrue(Files.isRegularFile(cache))
        assertTrue(Files.size(cache) < keys.size * 100L)
        TileHistoryStore(directory).use { store ->
            assertTrue(store.loadedFromIndexCache)
            for (key in keys) {
                assertEquals(7, store.readPixel(key, 0, 7))
                assertEquals(9, store.readPixel(key, 1, 7))
                assertEquals(11, store.readPixel(key, 2, 100))
                assertContentEquals(masked, assertNotNull(store.read(key, 2)).colors)
            }
        }
    }

    @Test
    fun packedIndexPreservesNearUniformSnapshotsAndSampledExceptions() = withStore { directory ->
        val keys = List(300) { TileKey(it, 0) }
        val varied = ByteArray(TileLayer.PIXELS) { it.toByte() }
        val nearUniform = ByteArray(TileLayer.PIXELS) { 7 }.apply { this[0] = 9 }
        TileHistoryStore(directory).use { store ->
            store.append(keys.map { TileLayer.full(it, 0, varied) })
            store.append(keys.map { TileLayer.snapshot(it, 17_000, nearUniform) })
        }
        assertTrue(Files.isRegularFile(directory.resolve(".index-cache.pidx")))
        TileHistoryStore(directory).use { store ->
            assertTrue(store.loadedFromIndexCache)
            for (key in keys) {
                assertEquals(9, store.readPixel(key, 17_000, 0))
                assertEquals(7, store.readPixel(key, 17_000, 136))
                assertContentEquals(
                    byteArrayOf(9, 7),
                    assertNotNull(store.readSamples(key, 17_000, intArrayOf(0, 136))).colors,
                )
                assertContentEquals(nearUniform, assertNotNull(store.read(key, 17_000)).colors)
            }
        }
    }

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
            assertEquals(5_027, result.bytesAdded)
            assertContentEquals(current, assertNotNull(store.read(key, 1000)).colors)
        }
        TileHistoryStore(directory).use { reopened ->
            assertEquals(1001, reopened.layerCount)
            assertContentEquals(current, assertNotNull(reopened.read(key, 1000)).colors)
        }
        assertTrue(Files.isRegularFile(directory.resolve(".index-cache.pidx")))
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
                store.append(
                    listOf(TileLayer(TileKey(0, 0), 0, longArrayOf(1, 0, 0, 0), byteArrayOf(1)))
                )
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
            assertEquals(first[12].toInt() and 255, store.readPixel(key, 5, 12))
            val sample = assertNotNull(store.readSamples(key, 4, intArrayOf(136)))
            assertEquals(initial[136], sample.colors[0])
            assertEquals(1, sample.bytesRead)
            assertNull(store.readPixel(key, -1, 12))
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
            assertEquals(false, reopened.hasChanges(key, 0, 4))
            assertEquals(true, reopened.hasChanges(key, 4, 5))
            assertEquals(true, reopened.hasChanges(key, 10, 4))
            assertEquals(false, reopened.hasChanges(TileKey(-1, -1), 0, 10))
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
                        val count =
                            if (random.nextInt(20) == 0) TileLayer.PIXELS
                            else 1 + random.nextInt(100)
                        repeat(count) {
                            val position = random.nextInt(TileLayer.PIXELS)
                            next[position] =
                                ((next[position].toInt() and 255) + 1 + random.nextInt(255))
                                    .toByte()
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
        // Sealing on close inserts a checkpoint at epoch 130, so the latest read decodes one layer.
        TileHistoryStore(directory).use { reopened ->
            val latest = assertNotNull(reopened.read(key, 130))
            assertContentEquals(current, latest.colors)
            assertEquals(1, latest.layersDecoded)
            assertEquals(0, latest.layersSkipped)
            assertEquals(131, reopened.layerCount)
            val earlier = assertNotNull(reopened.read(key, 129))
            assertEquals(129, earlier.colors[7].toInt() and 255)
            assertEquals(64, earlier.layersSkipped)
            assertEquals(2, earlier.layersDecoded)
        }
        TileHistoryStore(directory, checkpointInterval = 1_000).use { uncheckpointed ->
            uncheckpointed.append(
                listOf(assertNotNull(TileLayer.changed(key, 131, current, initial)))
            )
            uncheckpointed.seal()
            val read = assertNotNull(uncheckpointed.read(key, 131))
            assertContentEquals(initial, read.colors)
            assertEquals(2, read.layersDecoded)
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
            for (position in intArrayOf(0, 63, 64, 127, 255)) {
                assertEquals(
                    checkNotNull(snapshots[epoch][key])[position].toInt() and 255,
                    store.readPixel(key, epoch.toLong(), position),
                )
            }
            val positions = intArrayOf(0, 63, 64, 127, 255)
            assertContentEquals(
                ByteArray(positions.size) { checkNotNull(snapshots[epoch][key])[positions[it]] },
                assertNotNull(store.readSamples(key, epoch.toLong(), positions)).colors,
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
