package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class MapPageCacheTest {
  @Test
  fun latestPagesReuseImagesAndOnlyChangedAncestorsRebuild() {
    val source = HashMap<TileKey, ByteArray>()
    val red = TileKey(0, 0)
    val blue = TileKey(16, 0)
    source[red] = ByteArray(TileLayer.PIXELS) { 1 }
    source[blue] = ByteArray(TileLayer.PIXELS) { 2 }
    val cache =
        MapPageCache({ key, _ -> source[key] }, intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253))
    val firstKey = MapPageKey.containingTile(red.x, red.z, 1)
    val otherKey = MapPageKey.containingTile(blue.x, blue.z, 1)
    val first = assertNotNull(cache.latest(firstKey))
    val other = assertNotNull(cache.latest(otherKey))
    assertSame(first.image, cache.latest(firstKey)?.image)
    assertEquals(0xFFFF0000.toInt(), first.colorAt(0, 0))
    assertEquals(0xFF0000FF.toInt(), other.colorAt(0, 0))

    source[red] = ByteArray(TileLayer.PIXELS) { 2 }
    cache.invalidate(listOf(red))
    val changed = assertNotNull(cache.latest(firstKey))
    assertNotSame(first.image, changed.image)
    assertEquals(0xFF0000FF.toInt(), changed.colorAt(0, 0))
    assertSame(other.image, cache.latest(otherKey)?.image)
  }

  @Test
  fun historicalPagesDoNotReplaceLatestAndMissingAreaIsTransparent() {
    val key = TileKey(-1, -1)
    val cache =
        MapPageCache(
            { tile, epoch ->
              if (tile != key) null else ByteArray(TileLayer.PIXELS) { if (epoch == 0L) 1 else 2 }
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
  }

  @Test
  fun downsamplingAveragesColorAndKeepsUnexploredTransparent() {
    val tile = TileKey(0, 0)
    val pixels = ByteArray(TileLayer.PIXELS) { if (it % 2 == 0) 1 else 2 }
    val cache =
        MapPageCache(
            { key, _ -> if (key == tile) pixels else null },
            intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253),
        )
    val page = assertNotNull(cache.latest(MapPageKey(0, 0, 1)))
    assertEquals(0xFF800080.toInt(), page.colorAt(0, 0))
    assertEquals(0, page.colorAt(20, 20))
  }
}
