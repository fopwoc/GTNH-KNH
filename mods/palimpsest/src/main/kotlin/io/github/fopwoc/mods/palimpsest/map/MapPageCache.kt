package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.LinkedHashMap

/** Builds fixed-size pages from one deterministic source sample per output pixel. */
class MapPageCache(
    private val readTile: (TileKey, Long) -> ByteArray?,
    palette: IntArray,
    private val maxLatestPages: Int = 128,
    private val hasChanged: ((TileKey, Long, Long) -> Boolean)? = null,
    private val readSamples: ((TileKey, Long, IntArray) -> ByteArray?)? = null,
) {
  init {
    require(palette.size == 256)
    require(maxLatestPages > 0)
  }

  private val colors = palette.copyOf()
  private val latest = pageCache()
  private val historical = pageCache()
  private var historicalEpoch: Long? = null
  private var reads = 0L

  @Synchronized
  fun invalidate(tiles: Collection<TileKey>) {
    for (tile in tiles) for (lod in 0..MapPageKey.MAX_LOD) {
      latest.remove(MapPageKey.containingTile(tile.x, tile.z, lod))
    }
    historical.clear()
    historicalEpoch = null
  }

  @Synchronized
  fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? =
      build(key, Long.MAX_VALUE, latest, checkActive)

  /** Keeps one historical time and patches only tiles changed between observations. */
  @Synchronized
  fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
    require(epoch >= 0)
    checkActive()
    val previous = historicalEpoch
    if (previous != epoch) {
      if (previous != null && hasChanged != null) {
        advanceHistorical(previous, epoch, hasChanged, checkActive)
      } else {
        historical.clear()
      }
      historicalEpoch = epoch
    }
    return build(key, epoch, historical, checkActive)
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

  private fun pageCache() =
      object : LinkedHashMap<MapPageKey, MapPageRaster?>(maxLatestPages, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>
        ): Boolean = size > maxLatestPages
      }

  private fun build(
      key: MapPageKey,
      epoch: Long,
      cache: MutableMap<MapPageKey, MapPageRaster?>,
      checkActive: () -> Unit,
  ): MapPageRaster? {
    if (cache.containsKey(key)) return cache[key]
    val pixels = ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
    val positions = samplePositions(key.lod)
    val outputSide = TileLayer.SIDE shr key.lod
    val tileSide = MapPageKey.SIDE / outputSide
    var present = false
    for (tileZ in 0 until tileSide) for (tileX in 0 until tileSide) {
      checkActive()
      val tile = TileKey(key.x * tileSide + tileX, key.z * tileSide + tileZ)
      val samples = load(tile, epoch, key.lod, positions)
      if (samples != null) {
        present = true
        writeSamples(pixels, tileX, tileZ, outputSide, samples)
      }
    }
    checkActive()
    val raster = if (present) MapPageRaster(pixels) else null
    cache[key] = raster
    return raster
  }

  private fun advanceHistorical(
      from: Long,
      to: Long,
      changed: (TileKey, Long, Long) -> Boolean,
      checkActive: () -> Unit,
  ) {
    val updates = HashMap<MapPageKey, MapPageRaster?>()
    for ((key, old) in historical.toList()) {
      val outputSide = TileLayer.SIDE shr key.lod
      val tileSide = MapPageKey.SIDE / outputSide
      val positions = samplePositions(key.lod)
      var pixels: ByteArray? = null
      for (tileZ in 0 until tileSide) for (tileX in 0 until tileSide) {
        checkActive()
        val tile = TileKey(key.x * tileSide + tileX, key.z * tileSide + tileZ)
        if (!changed(tile, from, to)) continue
        if (pixels == null)
            pixels = old?.copyPixels() ?: ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
        writeSamples(pixels, tileX, tileZ, outputSide, load(tile, to, key.lod, positions))
      }
      if (pixels != null) {
        updates[key] =
            if (pixels.indices.step(4).any { pixels[it + 3].toInt() != 0 }) MapPageRaster(pixels)
            else null
      }
    }
    checkActive()
    historical.putAll(updates)
  }

  private fun load(key: TileKey, epoch: Long, lod: Int, positions: IntArray): ByteArray? {
    reads++
    if (lod == 0) return readTile(key, epoch)
    return readSamples?.invoke(key, epoch, positions)
        ?: if (readSamples == null) {
          readTile(key, epoch)?.let { tile -> ByteArray(positions.size) { tile[positions[it]] } }
        } else null
  }

  private fun samplePositions(lod: Int): IntArray {
    val step = 1 shl lod
    val side = TileLayer.SIDE / step
    return IntArray(side * side) { index ->
      val x = (index % side) * step + step / 2
      val z = (index / side) * step + step / 2
      z * TileLayer.SIDE + x
    }
  }

  private fun writeSamples(
      pixels: ByteArray,
      tileX: Int,
      tileZ: Int,
      side: Int,
      samples: ByteArray?,
  ) {
    require(samples == null || samples.size == side * side)
    for (z in 0 until side) for (x in 0 until side) {
      val target = ((tileZ * side + z) * MapPageKey.SIDE + tileX * side + x) * 4
      if (samples == null) {
        pixels.fill(0, target, target + 4)
      } else {
        val color = colors[samples[z * side + x].toInt() and 255]
        pixels[target] = (color ushr 16).toByte()
        pixels[target + 1] = (color ushr 8).toByte()
        pixels[target + 2] = color.toByte()
        pixels[target + 3] = 0xFF.toByte()
      }
    }
  }
}
