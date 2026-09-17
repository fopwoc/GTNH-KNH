package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.LinkedHashMap

/** Derives raster pages from historical tiles. Only the latest view retains pages across reads. */
class MapPageCache(
    private val readTile: (TileKey, Long) -> ByteArray?,
    palette: IntArray,
    private val maxLatestPages: Int = 128,
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

  /** Use after a successful append. Old historical pages are never modified. */
  @Synchronized
  fun invalidate(tiles: Collection<TileKey>) {
    for (tile in tiles) {
      for (lod in 0..MapPageKey.MAX_LOD) {
        latest.remove(MapPageKey.containingTile(tile.x, tile.z, lod))
      }
    }
  }

  @Synchronized fun latest(key: MapPageKey): MapPageRaster? = build(key, Long.MAX_VALUE, latest)

  /** Historical pages are temporary; scrubbing does not retain every epoch in memory. */
  @Synchronized
  fun historical(key: MapPageKey, epoch: Long): MapPageRaster? {
    require(epoch >= 0)
    return build(key, epoch, HashMap())
  }

  @Synchronized fun cachedLatestPages(): Int = latest.size

  @Synchronized
  fun clear() {
    latest.clear()
  }

  private fun build(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
  ): MapPageRaster? {
    if (cache.containsKey(key)) return cache[key]
    val raster = if (key.lod == 0) base(key, epoch) else downsample(key, epoch, cache)
    cache[key] = raster
    return raster
  }

  private fun base(key: MapPageKey, epoch: Long): MapPageRaster? {
    val pixels = ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
    var present = false
    for (tileZ in 0 until MapPageKey.BASE_TILES) {
      for (tileX in 0 until MapPageKey.BASE_TILES) {
        val tile =
            readTile(
                TileKey(
                    key.x * MapPageKey.BASE_TILES + tileX,
                    key.z * MapPageKey.BASE_TILES + tileZ,
                ),
                epoch,
            ) ?: continue
        require(tile.size == TileLayer.PIXELS)
        present = true
        for (z in 0 until TileLayer.SIDE) for (x in 0 until TileLayer.SIDE) {
          val color = colors[tile[z * TileLayer.SIDE + x].toInt() and 255]
          val target =
              ((tileZ * TileLayer.SIDE + z) * MapPageKey.SIDE + tileX * TileLayer.SIDE + x) * 4
          pixels[target] = (color ushr 16).toByte()
          pixels[target + 1] = (color ushr 8).toByte()
          pixels[target + 2] = color.toByte()
          pixels[target + 3] = 0xFF.toByte()
        }
      }
    }
    return if (present) MapPageRaster(pixels) else null
  }

  private fun downsample(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
  ): MapPageRaster? {
    val children =
        Array(4) { child ->
          build(
              MapPageKey(key.x * 2 + (child and 1), key.z * 2 + (child ushr 1), key.lod - 1),
              epoch,
              cache,
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
