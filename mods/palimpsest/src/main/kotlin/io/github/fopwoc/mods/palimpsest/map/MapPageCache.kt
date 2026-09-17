package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.LinkedHashMap

/** Derives raster pages from tiles, retaining the latest view and one historical working set. */
class MapPageCache(
    private val readTile: (TileKey, Long) -> ByteArray?,
    palette: IntArray,
    private val maxLatestPages: Int = 128,
    private val hasChanged: ((TileKey, Long, Long) -> Boolean)? = null,
) {
  init {
    require(palette.size == 256)
    require(maxLatestPages > 0)
  }

  private val colors = palette.copyOf()
  private val latest =
      object : LinkedHashMap<MapPageKey, MapPageRaster?>(maxLatestPages, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>
        ): Boolean = size > maxLatestPages
      }
  private val historical =
      object : LinkedHashMap<MapPageKey, MapPageRaster?>(maxLatestPages, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>
        ): Boolean = size > maxLatestPages
      }
  private var historicalEpoch: Long? = null
  private var reads = 0L

  /** Use after a successful append. Old historical pages are never modified. */
  @Synchronized
  fun invalidate(tiles: Collection<TileKey>) {
    for (tile in tiles) {
      for (lod in 0..MapPageKey.MAX_LOD) {
        latest.remove(MapPageKey.containingTile(tile.x, tile.z, lod))
      }
    }
    historical.clear()
    historicalEpoch = null
  }

  @Synchronized fun latest(key: MapPageKey): MapPageRaster? = buildRoot(key, Long.MAX_VALUE, latest)

  /** Retains one historical epoch. Small time moves patch only tiles with changed layers. */
  @Synchronized
  fun historical(key: MapPageKey, epoch: Long): MapPageRaster? {
    require(epoch >= 0)
    val previous = historicalEpoch
    if (previous != epoch) {
      historical.entries
          .toList()
          .filter { it.value == null && !hasPositiveAncestor(it.key) }
          .map { it.key }
          .forEach(historical::remove)
      if (previous != null && hasChanged != null && canAdvanceHistorical()) {
        advanceHistorical(previous, epoch, hasChanged)
      } else {
        historical.clear()
      }
      historicalEpoch = epoch
    }
    return buildRoot(key, epoch, historical)
  }

  @Synchronized fun cachedLatestPages(): Int = latest.size

  @Synchronized fun cachedHistoricalPages(): Int = historical.size

  @Synchronized fun tileReadCount(): Long = reads

  @Synchronized
  fun clear() {
    latest.clear()
    historical.clear()
    historicalEpoch = null
  }

  private fun hasPositiveAncestor(key: MapPageKey): Boolean {
    for (lod in key.lod + 1..MapPageKey.MAX_LOD) {
      val shift = 1 shl (lod - key.lod)
      if (
          historical[MapPageKey(Math.floorDiv(key.x, shift), Math.floorDiv(key.z, shift), lod)] !=
              null
      )
          return true
    }
    return false
  }

  private fun canAdvanceHistorical(): Boolean {
    for (page in historical.keys) {
      if (page.lod == 0) continue
      val baseSide = 1 shl page.lod
      for (z in 0 until baseSide) for (x in 0 until baseSide) {
        if (!historical.containsKey(MapPageKey(page.x * baseSide + x, page.z * baseSide + z, 0)))
            return false
      }
    }
    return true
  }

  private fun advanceHistorical(
      from: Long,
      to: Long,
      changed: (TileKey, Long, Long) -> Boolean,
  ) {
    val dirty = HashSet<MapPageKey>()
    for (page in historical.keys.filter { it.lod == 0 }) {
      var updated: ByteArray? = null
      for (tileZ in 0 until MapPageKey.BASE_TILES) for (tileX in 0 until MapPageKey.BASE_TILES) {
        val tile =
            TileKey(
                page.x * MapPageKey.BASE_TILES + tileX,
                page.z * MapPageKey.BASE_TILES + tileZ,
            )
        if (!changed(tile, from, to)) continue
        if (updated == null) {
          updated =
              historical[page]?.copyPixels() ?: ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
        }
        writeTile(updated, tileX, tileZ, loadTile(tile, to))
        for (lod in 1..MapPageKey.MAX_LOD) {
          dirty += MapPageKey.containingTile(tile.x, tile.z, lod)
        }
      }
      if (updated != null) {
        historical[page] =
            if (updated.indices.step(4).any { updated[it + 3].toInt() != 0 }) MapPageRaster(updated)
            else null
      }
    }
    dirty.forEach(historical::remove)
  }

  private fun buildRoot(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
  ): MapPageRaster? {
    if (cache.containsKey(key)) return cache[key]
    val staged = HashMap<MapPageKey, MapPageRaster?>()
    val raster = build(key, epoch, cache, staged)
    if (raster == null) cache[key] = null else cache.putAll(staged)
    return raster
  }

  private fun build(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
      staged: MutableMap<MapPageKey, MapPageRaster?>,
  ): MapPageRaster? {
    if (cache.containsKey(key)) return cache[key]
    if (staged.containsKey(key)) return staged[key]
    val raster = if (key.lod == 0) base(key, epoch) else downsample(key, epoch, cache, staged)
    staged[key] = raster
    return raster
  }

  private fun base(key: MapPageKey, epoch: Long): MapPageRaster? {
    val pixels = ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
    var present = false
    for (tileZ in 0 until MapPageKey.BASE_TILES) {
      for (tileX in 0 until MapPageKey.BASE_TILES) {
        val tile =
            loadTile(
                TileKey(
                    key.x * MapPageKey.BASE_TILES + tileX,
                    key.z * MapPageKey.BASE_TILES + tileZ,
                ),
                epoch,
            ) ?: continue
        present = true
        writeTile(pixels, tileX, tileZ, tile)
      }
    }
    return if (present) MapPageRaster(pixels) else null
  }

  private fun loadTile(key: TileKey, epoch: Long): ByteArray? {
    reads++
    return readTile(key, epoch)
  }

  private fun writeTile(pixels: ByteArray, tileX: Int, tileZ: Int, tile: ByteArray?) {
    require(tile == null || tile.size == TileLayer.PIXELS)
    for (z in 0 until TileLayer.SIDE) for (x in 0 until TileLayer.SIDE) {
      val target = ((tileZ * TileLayer.SIDE + z) * MapPageKey.SIDE + tileX * TileLayer.SIDE + x) * 4
      if (tile == null) {
        pixels.fill(0, target, target + 4)
      } else {
        val color = colors[tile[z * TileLayer.SIDE + x].toInt() and 255]
        pixels[target] = (color ushr 16).toByte()
        pixels[target + 1] = (color ushr 8).toByte()
        pixels[target + 2] = color.toByte()
        pixels[target + 3] = 0xFF.toByte()
      }
    }
  }

  private fun downsample(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
      staged: MutableMap<MapPageKey, MapPageRaster?>,
  ): MapPageRaster? {
    val children =
        Array(4) { child ->
          build(
              MapPageKey(key.x * 2 + (child and 1), key.z * 2 + (child ushr 1), key.lod - 1),
              epoch,
              cache,
              staged,
          )
        }
    if (children.all { it == null }) return null
    val side = MapPageKey.SIDE
    val result = ByteArray(side * side * 4)
    for (z in 0 until side) for (x in 0 until side) {
      var alpha = 0
      var red = 0
      var green = 0
      var blue = 0
      for (dz in 0..1) for (dx in 0..1) {
        val sourceX = x * 2 + dx
        val sourceZ = z * 2 + dz
        val child = children[(sourceZ / side) * 2 + sourceX / side] ?: continue
        val at = ((sourceZ % side) * side + sourceX % side) * 4
        val a = child.component(at, 3)
        alpha += a
        red += child.component(at, 0) * a
        green += child.component(at, 1) * a
        blue += child.component(at, 2) * a
      }
      if (alpha == 0) continue
      val at = (z * side + x) * 4
      result[at] = ((red + alpha / 2) / alpha).toByte()
      result[at + 1] = ((green + alpha / 2) / alpha).toByte()
      result[at + 2] = ((blue + alpha / 2) / alpha).toByte()
      result[at + 3] = (alpha / 4).toByte()
    }
    return MapPageRaster(result)
  }
}
