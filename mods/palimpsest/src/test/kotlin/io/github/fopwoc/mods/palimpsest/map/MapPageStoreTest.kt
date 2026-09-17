package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MapPageStoreTest {
  @Test
  fun appendInvalidatesLatestButPreservesHistoricalReads() {
    val directory = Files.createTempDirectory("palimpsest-map-pages-")
    val tile = TileKey(0, 0)
    val page = MapPageKey.containingTile(tile.x, tile.z, 0)
    val palette = intArrayOf(0xFF0000, 0x0000FF) + IntArray(254)
    try {
      MapPageStore(directory, palette, maxOpenRegions = 1).use { store ->
        store.append(listOf(TileLayer.full(tile, 0, ByteArray(TileLayer.PIXELS))))
        val first = assertNotNull(store.latest(page))
        assertEquals(0xFFFF0000.toInt(), first.colorAt(0, 0))
        assertSame(first.image, store.latest(page)?.image)

        val noChange = store.append(listOf(TileLayer.full(tile, 1, ByteArray(TileLayer.PIXELS))))
        assertEquals(0, noChange.layersWritten)
        assertSame(first.image, store.latest(page)?.image)

        store.append(listOf(TileLayer.full(tile, 2, ByteArray(TileLayer.PIXELS) { 1 })))
        val changed = assertNotNull(store.latest(page))
        assertNotSame(first.image, changed.image)
        assertEquals(0xFF0000FF.toInt(), changed.colorAt(0, 0))
        assertEquals(0xFFFF0000.toInt(), assertNotNull(store.historical(page, 0)).colorAt(0, 0))
        store.reload()
        assertEquals(0xFF0000FF.toInt(), assertNotNull(store.latest(page)).colorAt(0, 0))
      }
    } finally {
      Files.walk(directory).use { files ->
        files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
      }
    }
  }
}
