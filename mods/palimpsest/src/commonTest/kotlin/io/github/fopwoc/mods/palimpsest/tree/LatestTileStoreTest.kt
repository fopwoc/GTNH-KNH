package io.github.fopwoc.mods.palimpsest.tree

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LatestTileStoreTest {
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
