package io.github.fopwoc.mods.palimpsest.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegionTileHistoryStoreTest {
    @Test
    fun invalidEpochInOneRegionRejectsTheWholeBatch() {
        val directory = Files.createTempDirectory("palimpsest-regions-")
        try {
            val first = TileKey(0, 0)
            val far = TileKey(64, 0)
            RegionTileHistoryStore(directory).use { store ->
                store.append(listOf(TileLayer.full(far, 5, ByteArray(TileLayer.PIXELS) { 1 })))
                assertFailsWith<IllegalArgumentException> {
                    store.append(
                        listOf(
                            TileLayer.full(first, 0, ByteArray(TileLayer.PIXELS) { 2 }),
                            TileLayer.full(far, 5, ByteArray(TileLayer.PIXELS) { 3 }),
                        )
                    )
                }
                assertNull(store.read(first, 0))
                assertEquals(1, store.read(far, 5)?.colors?.get(0))
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun negativeRegionsReopenAfterEvictionWithoutLosingHistory() {
        val directory = Files.createTempDirectory("palimpsest-regions-")
        try {
            val keys = listOf(TileKey(-33, 0), TileKey(-1, 0), TileKey(0, 0), TileKey(32, 0))
            RegionTileHistoryStore(directory, maxOpenRegions = 2).use { store ->
                val result =
                    store.append(
                        keys.mapIndexed { index, key ->
                            TileLayer.full(key, 0, ByteArray(TileLayer.PIXELS) { index.toByte() })
                        }
                    )
                assertEquals(4, result.layersWritten)
                assertEquals(2, store.openRegionCount())
                for ((index, key) in keys.withIndex()) {
                    assertContentEquals(
                        ByteArray(TileLayer.PIXELS) { index.toByte() },
                        assertNotNull(store.read(key, 0)).colors,
                    )
                    assertEquals(2, store.openRegionCount())
                }
            }
            RegionTileHistoryStore(directory, maxOpenRegions = 1).use { reopened ->
                for ((index, key) in keys.withIndex()) {
                    assertContentEquals(
                        ByteArray(TileLayer.PIXELS) { index.toByte() },
                        assertNotNull(reopened.read(key, 0)).colors,
                    )
                    assertEquals(1, reopened.openRegionCount())
                }
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
