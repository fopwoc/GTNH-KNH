package io.github.fopwoc.mods.palimpsest.tree

import io.github.fopwoc.mods.palimpsest.storage.StorageWrites
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LatestTileStoreTest {
    @Test
    fun compactingOneRegionKeepsOtherRegionsPendingForMaintenance() = withDirectory { directory ->
        val writes = StorageWrites()
        val cold = TileKey(32, 0)
        val hot = TileKey(0, 0)
        LatestTileStore(directory, 1, writes = writes).use { store ->
            store.write(1, mapOf(cold to TileRecord.solid(1, 7)))
            for (epoch in 2L..400L) store.write(
                epoch,
                mapOf(hot to TileRecord.solid(epoch, epoch.toInt())),
            )
            val before = writes.snapshot()
            assertTrue(before.compactions > 0)
            assertEquals(before.compactions, before.flushes)
            assertTrue(store.sealIfDue())
            assertEquals(before.flushes + 2, writes.snapshot().flushes)
            assertEquals(false, store.sealIfDue())
        }
        LatestTileStore(directory, 1).use { store ->
            assertEquals(7, assertNotNull(store.tile(cold, Long.MAX_VALUE)).block(0))
            assertEquals(400, assertNotNull(store.tile(hot, Long.MAX_VALUE)).block(0))
        }
    }

    @Test
    fun batchesStayReadableUntilMaintenanceFlushAndCloseFlushesTheRemainder() =
        withDirectory { directory ->
            val writes = StorageWrites()
            val key = TileKey(-1, 0)
            LatestTileStore(directory, 1, writes = writes).use { store ->
                repeat(20) { at ->
                    val epoch = at + 1L
                    store.write(epoch, mapOf(key to TileRecord.solid(epoch, at + 1)))
                }
                assertEquals(20, assertNotNull(store.tile(key, Long.MAX_VALUE)).block(0))
                assertEquals(0, writes.snapshot().flushes)
                assertEquals(store.diskBytes(), writes.snapshot().totalBytes)
                assertTrue(store.sealIfDue())
                assertEquals(1, writes.snapshot().flushes)
                assertEquals(false, store.sealIfDue())
                store.write(21, mapOf(key to TileRecord.solid(21, 21)))
                val before = writes.snapshot().totalBytes
                store.write(22, mapOf(key to TileRecord.solid(22, 21)))
                assertEquals(before, writes.snapshot().totalBytes)
            }
            assertEquals(2, writes.snapshot().flushes)
            LatestTileStore(directory, 1).use { store ->
                assertEquals(21, assertNotNull(store.tile(key, Long.MAX_VALUE)).block(0))
            }
        }

    @Test
    fun byteAndRegionBoundsFlushWithoutWaitingForMaintenance() = withDirectory { directory ->
        val writes = StorageWrites()
        LatestTileStore(directory.resolve("bytes"), 1, writes = writes, flushBytes = 1).use {
            it.write(1, mapOf(TileKey(0, 0) to TileRecord.solid(1, 7)))
            assertEquals(1, writes.snapshot().flushes)
            assertEquals(false, it.sealIfDue())
        }
        val bounded = StorageWrites()
        LatestTileStore(directory.resolve("regions"), 1, writes = bounded, maxPendingRegions = 2)
            .use { store ->
                val keys = listOf(TileKey(0, 0), TileKey(32, 0), TileKey(64, 0))
                store.write(1, keys.associateWith { TileRecord.solid(1, 7) })
                assertEquals(2, bounded.snapshot().flushes)
                assertTrue(store.sealIfDue())
                assertEquals(3, bounded.snapshot().flushes)
                for (key in keys) assertEquals(
                    7,
                    assertNotNull(store.tile(key, Long.MAX_VALUE)).block(0),
                )
            }
    }

    @Test
    fun compactionBytesAreCountedAndBothPoliciesProduceIdenticalFiles() =
        withDirectory { directory ->
            val counters = listOf(StorageWrites(), StorageWrites())
            for ((mode, writes) in counters.withIndex()) {
                LatestTileStore(
                        directory.resolve("mode$mode"),
                        1,
                        writes = writes,
                        flushBytes = if (mode == 0) 0 else LatestTileStore.DEFAULT_FLUSH_BYTES,
                    )
                    .use { store ->
                        for (epoch in 1L..400L) store.write(
                            epoch,
                            mapOf(TileKey(0, 0) to TileRecord.solid(epoch, epoch.toInt())),
                        )
                        assertTrue(writes.snapshot().compactions > 0)
                        assertTrue(
                            writes
                                .snapshot()
                                .bytes
                                .getValue(StorageWrites.Kind.CURRENT_COMPACTION) > 0
                        )
                        assertTrue(writes.snapshot().totalBytes > store.diskBytes())
                    }
            }
            assertTrue(
                Files.readAllBytes(directory.resolve("mode0/0_0.preg"))
                    .contentEquals(Files.readAllBytes(directory.resolve("mode1/0_0.preg")))
            )
            assertEquals(counters[0].snapshot().bytes, counters[1].snapshot().bytes)
            assertEquals(counters[0].snapshot().compactions, counters[1].snapshot().compactions)
            assertTrue(counters[1].snapshot().flushes < counters[0].snapshot().flushes / 10)
        }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("palimpsest-current-region-")
        try {
            test(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun regionsPackNegativeCoordinatesAndPreserveUntouchedTilesThroughCompaction() =
        withDirectory { directory ->
            val keys = listOf(TileKey(-1, -1), TileKey(-32, -32), TileKey(-33, 0), TileKey(32, 32))
            LatestTileStore(directory, 1).use { store ->
                assertEquals(
                    keys.size,
                    store.write(1, keys.associateWith { TileRecord.solid(1, 1, 64, biome = 7) }),
                )
                for (epoch in 2L..160L) store.write(
                    epoch,
                    mapOf(keys[0] to TileRecord.solid(epoch, epoch.toInt(), 64, biome = 7)),
                )
                assertTrue(
                    store.diskBytes() < 8192,
                    "compaction should bound superseded records: ${store.diskBytes()}",
                )
                assertEquals(
                    0,
                    store.write(161, mapOf(keys[0] to TileRecord.solid(161, 160, 64, biome = 7))),
                )
            }
            LatestTileStore(directory, 2, { machine, id -> if (machine == 1) id + 1000 else id })
                .use { store ->
                    assertEquals(keys.toSet(), store.keys())
                    assertEquals(160, store.latestEpoch)
                    assertEquals(1160, assertNotNull(store.tile(keys[0], Long.MAX_VALUE)).block(0))
                    for (key in keys.drop(1)) assertEquals(
                        1001,
                        assertNotNull(store.tile(key, Long.MAX_VALUE)).block(0),
                    )
                    assertEquals(
                        1160,
                        Sample(
                                store
                                    .samples(
                                        0,
                                        MapTree.squareX(keys[0], 0),
                                        MapTree.squareZ(keys[0], 0),
                                        1,
                                        Long.MAX_VALUE,
                                    )[0]
                            )
                            .block,
                    )
                }
        }

    @Test
    fun denseRegionIsCompressedAndUsesOneFile() = withDirectory { directory ->
        LatestTileStore(directory, 1).use { store ->
            val tiles =
                (0 until 1024).associate { tile ->
                    TileKey(tile % 32, tile / 32) to
                        TileRecord.build(
                            1,
                            { 1 + (it % 16 / 6 + tile % 3) },
                            { 60 + it % 16 / 3 + it / 16 / 4 + tile % 2 },
                            biome = { if (it % 16 < 8) 1 else 6 },
                        )
                }
            store.write(1, tiles)
            assertTrue(store.diskBytes() < 150_000, "region bytes: ${store.diskBytes()}")
            Files.list(directory).use { paths ->
                assertEquals(1, paths.filter { it.toString().endsWith(".preg") }.count())
            }
            for ((key, record) in tiles) assertEquals(record, store.tile(key, Long.MAX_VALUE))
        }
    }

    @Test
    fun truncatedAppendKeepsTheLastCompleteGroup() = withDirectory { directory ->
        val key = TileKey(0, 0)
        LatestTileStore(directory, 1).use { it.write(1, mapOf(key to TileRecord.solid(1, 7))) }
        val region = directory.resolve("0_0.preg")
        val validSize = Files.size(region)
        Files.write(region, byteArrayOf(0, 0, 0, 20, 1, 2, 3), StandardOpenOption.APPEND)
        LatestTileStore(directory, 1).use { store ->
            assertEquals(7, assertNotNull(store.tile(key, Long.MAX_VALUE)).block(0))
            assertEquals(validSize, Files.size(region))
            store.write(2, mapOf(key to TileRecord.solid(2, 9)))
        }
        LatestTileStore(directory, 1).use {
            assertEquals(9, assertNotNull(it.tile(key, Long.MAX_VALUE)).block(0))
        }
    }

    @Test
    fun damagedTileChecksumIsDetectedOnRead() = withDirectory { directory ->
        LatestTileStore(directory, 1).use { store ->
            val key = TileKey(0, 0)
            store.write(1, mapOf(key to TileRecord.solid(1, 7)))
            val region = directory.resolve("0_0.preg")
            val bytes = Files.readAllBytes(region)
            bytes[bytes.lastIndex - 4] = (bytes[bytes.lastIndex - 4].toInt() xor 1).toByte()
            Files.write(region, bytes)
            assertFailsWith<CorruptTreeException> { store.tile(key, Long.MAX_VALUE) }
        }
    }
}
