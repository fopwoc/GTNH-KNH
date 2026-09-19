package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class MapPageStoreTest {
    @Test
    fun observationsRenderLiveAndCommitOnScheduleAndOnClose() {
        val directory = Files.createTempDirectory("palimpsest-map-observe-")
        val tile = TileKey(2, 3)
        val page = MapPageKey.containingTile(tile.x, tile.z, 0)
        val palette = intArrayOf(0, 0xFF0000, 0x0000FF, 0x00FF00) + IntArray(252)
        var now = 10_000L
        try {
            MapPageStore(
                    directory,
                    palette,
                    commitInterval = Duration.ofSeconds(60),
                    clock = { now },
                )
                .use { store ->
                    store.observe(tile, ByteArray(TileLayer.PIXELS) { 1 })
                    assertEquals(
                        0xFFFF0000.toInt(),
                        assertNotNull(store.latest(page)).colorAt(32, 48),
                    )
                    assertEquals(1, store.commitDue())
                    now += 1_000
                    store.observe(tile, ByteArray(TileLayer.PIXELS) { 2 })
                    // Live page shows the new colors although history still holds the old ones.
                    assertEquals(
                        0xFF0000FF.toInt(),
                        assertNotNull(store.latest(page)).colorAt(32, 48),
                    )
                    assertEquals(
                        0xFFFF0000.toInt(),
                        assertNotNull(store.historical(page, now)).colorAt(32, 48),
                    )
                    assertEquals(0, store.commitDue())
                    now += 60_000
                    store.observe(tile, ByteArray(TileLayer.PIXELS) { 3 })
                    assertEquals(1, store.commitDue())
                    assertEquals(
                        0xFF00FF00.toInt(),
                        assertNotNull(store.historical(page, now)).colorAt(32, 48),
                    )
                    now += 1_000
                    store.observe(tile, ByteArray(TileLayer.PIXELS) { 2 })
                }
            MapPageStore(directory, palette).use { reopened ->
                assertEquals(
                    0xFF0000FF.toInt(),
                    assertNotNull(reopened.latest(page)).colorAt(32, 48),
                )
                // Close committed the last observation at `now`; just before it, history is green.
                assertEquals(
                    0xFF0000FF.toInt(),
                    assertNotNull(reopened.historical(page, now)).colorAt(32, 48),
                )
                assertEquals(
                    0xFF00FF00.toInt(),
                    assertNotNull(reopened.historical(page, now - 1)).colorAt(32, 48),
                )
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun appendInvalidatesLatestButPreservesHistoricalReads() {
        val directory = Files.createTempDirectory("palimpsest-map-pages-")
        val tile = TileKey(0, 0)
        val page = MapPageKey.containingTile(tile.x, tile.z, 0)
        val palette = intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253)
        try {
            MapPageStore(directory, palette, maxOpenRegions = 1).use { store ->
                store.append(listOf(TileLayer.full(tile, 0, ByteArray(TileLayer.PIXELS) { 1 })))
                val first = assertNotNull(store.latest(page))
                assertEquals(0xFFFF0000.toInt(), first.colorAt(0, 0))
                assertSame(first.image, store.latest(page)?.image)

                val noChange =
                    store.append(listOf(TileLayer.full(tile, 1, ByteArray(TileLayer.PIXELS) { 1 })))
                assertEquals(0, noChange.layersWritten)
                assertSame(first.image, store.latest(page)?.image)

                store.append(listOf(TileLayer.full(tile, 2, ByteArray(TileLayer.PIXELS) { 2 })))
                val changed = assertNotNull(store.latest(page))
                assertNotSame(first.image, changed.image)
                assertEquals(0xFF0000FF.toInt(), changed.colorAt(0, 0))
                assertEquals(
                    0xFFFF0000.toInt(),
                    assertNotNull(store.historical(page, 0)).colorAt(0, 0),
                )
                store.flush()
            }
            MapPageStore(directory, palette).use { reopened ->
                assertEquals(0xFF0000FF.toInt(), assertNotNull(reopened.latest(page)).colorAt(0, 0))
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }

    @Test
    fun wideChannelSurvivesLiveViewHistoryAndReopen() {
        val directory = Files.createTempDirectory("palimpsest-map-wide-")
        val tile = TileKey(0, 0)
        val page = MapPageKey.containingTile(tile.x, tile.z, 0)
        val channels = listOf(MapChannel.COLORS, MapChannel("ids", bytes = 2))
        // Channel 1 becomes the color, so the page shows exactly what the wide channel holds.
        val shader = PixelShader { values -> values[1] or (0xFF shl 24) }
        var now = 10_000L
        fun open() = MapPageStore(directory, channels, shader, clock = { now })
        try {
            open().use { store ->
                store.observe(tile, IntArray(TileLayer.PIXELS) { 1 }, IntArray(TileLayer.PIXELS) { 0x1234 })
                assertEquals(0xFF001234.toInt(), assertNotNull(store.latest(page)).colorAt(0, 0))
                assertEquals(1, store.commitDue())
                now += 1_000
                store.observe(tile, IntArray(TileLayer.PIXELS) { 1 }, IntArray(TileLayer.PIXELS) { 0x1299 })
                assertEquals(0xFF001299.toInt(), assertNotNull(store.latest(page)).colorAt(0, 0))
                // Only the low plane changed, so history reads the high byte from the older layer.
                assertEquals(
                    0xFF001234.toInt(),
                    assertNotNull(store.historical(page, now - 1)).colorAt(0, 0),
                )
            }
            open().use { reopened ->
                assertEquals(0xFF001299.toInt(), assertNotNull(reopened.latest(page)).colorAt(0, 0))
                assertEquals(
                    0xFF001234.toInt(),
                    assertNotNull(reopened.historical(page, now - 1)).colorAt(0, 0),
                )
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
