package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class MapPageCacheTest {
    @Test
    fun distantLodsUseBoundedWorldAnchoredSamples() {
        val requested = ArrayList<TileKey>()
        val cache =
            MapPageCache(
                { key, _ ->
                    requested += key
                    ByteArray(TileLayer.PIXELS) { 1 }
                },
                intArrayOf(0, 0xFF0000) + IntArray(254),
                readSamples = { key, _, positions ->
                    requested += key
                    assertContentEquals(intArrayOf(136), positions)
                    byteArrayOf(1)
                },
            )
        val page = assertNotNull(cache.latest(MapPageKey(0, 0, 7)))
        assertEquals(256, requested.size)
        assertEquals(TileKey(0, 0), requested.first())
        assertEquals(TileKey(960, 960), requested.last())
        assertEquals(0xFFFF0000.toInt(), page.colorAt(0, 0))
        assertEquals(0xFFFF0000.toInt(), page.colorAt(7, 7))
        assertEquals(0xFFFF0000.toInt(), page.colorAt(8, 8))
        requested.clear()
        cache.latest(MapPageKey(-1, -1, 7))
        assertEquals(TileKey(-1024, -1024), requested.first())
    }

    @Test
    fun distantHistoryIgnoresChangesToSkippedTiles() {
        val sampled = TileKey(0, 0)
        val skipped = TileKey(1, 0)
        var reads = 0
        val cache =
            MapPageCache(
                { _, _ -> null },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
                hasChanged = { key, from, to ->
                    (key == skipped && from == 0L && to == 1L) ||
                        (key == sampled && from == 1L && to == 2L)
                },
                readSamples = { key, epoch, _ ->
                    reads++
                    if (key == sampled) byteArrayOf(if (epoch < 2) 1 else 2) else null
                },
            )
        val page = MapPageKey(0, 0, 7)
        val first = assertNotNull(cache.historical(page, 0))
        reads = 0
        assertSame(first.image, cache.historical(page, 1)?.image)
        assertEquals(0, reads)
        val second = assertNotNull(cache.historical(page, 2))
        assertEquals(1, reads)
        assertEquals(0xFF0000FF.toInt(), second.colorAt(0, 0))
    }

    @Test
    fun latestPagesReuseImagesAndOnlyChangedAncestorsRebuild() {
        val source = HashMap<TileKey, ByteArray>()
        val red = TileKey(0, 0)
        val blue = TileKey(16, 0)
        source[red] = ByteArray(TileLayer.PIXELS) { 1 }
        source[blue] = ByteArray(TileLayer.PIXELS) { 2 }
        val cache =
            MapPageCache(
                { key, _ -> source[key] },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
            )
        val firstKey = MapPageKey.containingTile(red.x, red.z, 1)
        val otherKey = MapPageKey.containingTile(blue.x, blue.z, 1)
        val first = assertNotNull(cache.latest(firstKey))
        val other = assertNotNull(cache.latest(otherKey))
        assertSame(first.image, cache.latest(firstKey)?.image)
        assertEquals(0xFFFF0000.toInt(), first.colorAt(0, 0))
        assertEquals(0xFF0000FF.toInt(), other.colorAt(0, 0))

        source[red] = ByteArray(TileLayer.PIXELS) { 2 }
        cache.invalidateTiles(listOf(red), 1)
        val changed = assertNotNull(cache.latest(firstKey))
        assertNotSame(first.image, changed.image)
        assertEquals(0xFF0000FF.toInt(), changed.colorAt(0, 0))
        assertSame(other.image, cache.latest(otherKey)?.image)
    }

    @Test
    fun appendsAfterThePinnedEpochKeepHistoricalPagesWarm() {
        val tile = TileKey(0, 0)
        var reads = 0
        val cache =
            MapPageCache(
                { key, _ ->
                    reads++
                    if (key == tile) ByteArray(TileLayer.PIXELS) { 1 } else null
                },
                intArrayOf(0, 0xFF0000) + IntArray(254),
                hasChanged = { _, _, _ -> false },
            )
        val page = MapPageKey(0, 0, 0)
        val pinned = assertNotNull(cache.historical(page, 10))
        assertNotNull(cache.latest(page))
        reads = 0
        cache.invalidate(listOf(TileLayer.full(tile, 11, ByteArray(TileLayer.PIXELS) { 1 })))
        assertSame(pinned.image, cache.historical(page, 10)?.image)
        assertEquals(0, reads)
        cache.latest(page)
        assertEquals(64, reads)
        reads = 0
        cache.invalidate(
            listOf(TileLayer.full(TileKey(1, 0), 10, ByteArray(TileLayer.PIXELS) { 1 }))
        )
        assertNotSame(pinned.image, cache.historical(page, 10)?.image)
        assertEquals(64, reads)
        reads = 0
        cache.invalidate(
            listOf(TileLayer.full(TileKey(8, 0), 3, ByteArray(TileLayer.PIXELS) { 1 }))
        )
        cache.historical(page, 10)
        assertEquals(0, reads)
    }

    @Test
    fun pageInvalidatedDuringItsBuildIsNotCached() {
        val tile = TileKey(0, 0)
        var color: Byte = 1
        lateinit var cache: MapPageCache
        var invalidateDuringBuild = false
        cache =
            MapPageCache(
                { key, _ ->
                    if (invalidateDuringBuild && key == TileKey(1, 0)) {
                        invalidateDuringBuild = false
                        color = 2
                        cache.invalidateTiles(listOf(tile), 0)
                    }
                    if (key == tile) ByteArray(TileLayer.PIXELS) { color } else null
                },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
            )
        val page = MapPageKey(0, 0, 0)
        invalidateDuringBuild = true
        val stale = assertNotNull(cache.latest(page))
        assertEquals(0xFFFF0000.toInt(), stale.colorAt(0, 0))
        assertEquals(0, cache.cachedLatestPages())
        val fresh = assertNotNull(cache.latest(page))
        assertEquals(0xFF0000FF.toInt(), fresh.colorAt(0, 0))
        assertEquals(1, cache.cachedLatestPages())
    }

    @Test
    fun historicalPagesDoNotReplaceLatestAndMissingAreaIsTransparent() {
        val key = TileKey(-1, -1)
        val cache =
            MapPageCache(
                { tile, epoch ->
                    if (tile != key) null
                    else ByteArray(TileLayer.PIXELS) { if (epoch == 0L) 1 else 2 }
                },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
            )
        val page = MapPageKey.containingTile(key.x, key.z, 1)
        val latest = assertNotNull(cache.latest(page))
        val old = assertNotNull(cache.historical(page, 0))
        assertEquals(0xFF0000FF.toInt(), latest.colorAt(120, 120))
        assertEquals(0xFFFF0000.toInt(), old.colorAt(120, 120))
        assertEquals(0, old.colorAt(0, 0))
        assertSame(latest.image, cache.latest(page)?.image)
        assertNull(cache.latest(MapPageKey(99, 99, 0)))
    }

    @Test
    fun cameraHandlesNegativePagesAndKeepsNearbyDrawingCoordinates() {
        val camera = MapCamera(-1.0, -1.0, 0.5, 256, 192)
        assertEquals(1, camera.lod)
        val keys = camera.visiblePages()
        assertEquals(true, MapPageKey(-1, -1, 1) in keys)
        assertEquals(true, MapPageKey(0, 0, 1) in keys)
        val dummy =
            io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage(
                128,
                128,
                ByteArray(128 * 128 * 4),
            )
        val draw = camera.draw(MapPageKey(0, 0, 1), dummy)
        assertEquals(128.5f, draw.x)
        assertEquals(96.5f, draw.y)
        val distant = MapCamera(0.0, 0.0, 1.0 / 4096.0, 256, 192)
        assertEquals(12, distant.lod)
        assertEquals(true, distant.visiblePages().isNotEmpty())
    }

    @Test
    fun zoomedPageSamplesSourceAndKeepsUnexploredTransparent() {
        val tile = TileKey(0, 0)
        val pixels = ByteArray(TileLayer.PIXELS) { if (it % 2 == 0) 1 else 2 }
        val cache =
            MapPageCache(
                { key, _ -> if (key == tile) pixels else null },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
            )
        val page = assertNotNull(cache.latest(MapPageKey(0, 0, 1)))
        assertEquals(0xFF0000FF.toInt(), page.colorAt(0, 0))
        assertEquals(0, page.colorAt(20, 20))
    }

    @Test
    fun nearbyHistoricalTimeMoveReadsOnlyChangedTiles() {
        val changedTile = TileKey(0, 0)
        var tileReads = 0
        val cache =
            MapPageCache(
                { key, epoch ->
                    tileReads++
                    if (key == changedTile)
                        ByteArray(TileLayer.PIXELS) { if (epoch == 0L) 1 else 2 }
                    else null
                },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
                hasChanged = { key, from, to -> key == changedTile && (from == 0L) != (to == 0L) },
            )
        val page = MapPageKey(0, 0, 3)
        val first = assertNotNull(cache.historical(page, 0))
        assertEquals(0xFFFF0000.toInt(), first.colorAt(0, 0))
        repeat(6) { cache.historical(MapPageKey(it + 1, 0, 3), 0) }
        tileReads = 0
        val next = assertNotNull(cache.historical(page, 1))
        assertEquals(1, tileReads)
        assertEquals(0xFF0000FF.toInt(), next.colorAt(0, 0))
        tileReads = 0
        assertSame(next.image, cache.historical(page, 2)?.image)
        assertEquals(0, tileReads)
        tileReads = 0
        val previous = assertNotNull(cache.historical(page, 0))
        assertEquals(1, tileReads)
        assertEquals(0xFFFF0000.toInt(), previous.colorAt(0, 0))
    }

    @Test
    fun canceledTimeMoveLeavesPreviousPageIntact() {
        val first = TileKey(0, 0)
        val second = TileKey(1, 0)
        var cancelSecondRead = false
        val cache =
            MapPageCache(
                { key, epoch ->
                    if (key !in setOf(first, second)) null
                    else {
                        if (cancelSecondRead && epoch == 1L && key == second) {
                            throw CancellationException("superseded")
                        }
                        ByteArray(TileLayer.PIXELS) { if (epoch == 0L) 1 else 2 }
                    }
                },
                intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
                hasChanged = { key, from, to -> key in setOf(first, second) && from != to },
            )
        val page = MapPageKey(0, 0, 0)
        val before = assertNotNull(cache.historical(page, 0))
        cancelSecondRead = true
        assertFailsWith<CancellationException> { cache.historical(page, 1) }
        assertSame(before.image, cache.historical(page, 0)?.image)
        assertEquals(0xFFFF0000.toInt(), before.colorAt(0, 0))
        assertEquals(0xFFFF0000.toInt(), before.colorAt(16, 0))
        cancelSecondRead = false
        val after = assertNotNull(cache.historical(page, 1))
        assertEquals(0xFF0000FF.toInt(), after.colorAt(0, 0))
        assertEquals(0xFF0000FF.toInt(), after.colorAt(16, 0))
    }

    @Test
    fun supersededPageBuildStopsBeforeScanningWholeLod() {
        var reads = 0
        val cache =
            MapPageCache(
                { key, _ ->
                    reads++
                    if (key == TileKey(0, 0)) ByteArray(TileLayer.PIXELS) { 1 } else null
                },
                intArrayOf(0, 0xFF0000) + IntArray(254),
            )
        val page = MapPageKey(0, 0, 3)
        assertFailsWith<CancellationException> {
            cache.latest(page) {
                if (reads >= 80) throw CancellationException("newer camera position")
            }
        }
        assertEquals(80, reads)
        assertEquals(0, cache.cachedLatestPages())
        assertEquals(0xFFFF0000.toInt(), assertNotNull(cache.latest(page)).colorAt(0, 0))
    }
}
