package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path

/** Region-paged history and its disposable derived LOD cache, with write invalidation together. */
class MapPageStore(directory: Path, palette: IntArray, maxOpenRegions: Int = 16) : AutoCloseable {
  private val history = RegionTileHistoryStore(directory, maxOpenRegions)
  private val pages = MapPageCache({ key, epoch -> history.read(key, epoch)?.colors }, palette)

  @Synchronized
  fun append(layers: List<TileLayer>): TileHistoryStore.AppendResult {
    val result = history.append(layers)
    if (result.layersWritten > 0) pages.invalidate(layers.map(TileLayer::key))
    return result
  }

  @Synchronized fun latest(key: MapPageKey): MapPageRaster? = pages.latest(key)

  @Synchronized
  fun historical(key: MapPageKey, epoch: Long): MapPageRaster? = pages.historical(key, epoch)

  @Synchronized
  fun reload() {
    history.close()
    pages.clear()
  }

  @Synchronized
  override fun close() {
    history.close()
    pages.clear()
  }
}
