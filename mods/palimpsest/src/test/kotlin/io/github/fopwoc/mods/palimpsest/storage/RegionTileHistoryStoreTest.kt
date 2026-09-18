package io.github.fopwoc.mods.palimpsest.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RegionTileHistoryStoreTest {
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
