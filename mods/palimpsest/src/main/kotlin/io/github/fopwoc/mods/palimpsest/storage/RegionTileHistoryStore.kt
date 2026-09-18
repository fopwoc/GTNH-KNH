package io.github.fopwoc.mods.palimpsest.storage

import java.nio.file.Path
import java.util.LinkedHashMap

/** Opens only the immutable segment indexes needed by the current working regions. */
class RegionTileHistoryStore(private val directory: Path, private val maxOpenRegions: Int = 256) :
    AutoCloseable {
  init {
    require(maxOpenRegions > 0)
  }

  private data class Region(val x: Int, val z: Int)

  private val open = LinkedHashMap<Region, TileHistoryStore>(maxOpenRegions, 0.75f, true)
  private var opened = 0L
  private var evicted = 0L

  @Synchronized
  fun read(key: TileKey, epoch: Long): TileHistoryStore.TileRead? = region(key).read(key, epoch)

  @Synchronized
  fun readPixel(key: TileKey, epoch: Long, position: Int): Int? =
      region(key).readPixel(key, epoch, position)

  @Synchronized
  fun readSamples(key: TileKey, epoch: Long, positions: IntArray): TileHistoryStore.SampleRead? =
      region(key).readSamples(key, epoch, positions)

  @Synchronized
  fun hasChanges(key: TileKey, firstEpoch: Long, secondEpoch: Long): Boolean =
      region(key).hasChanges(key, firstEpoch, secondEpoch)

  @Synchronized
  fun append(layers: List<TileLayer>): TileHistoryStore.AppendResult {
    var written = 0
    var discarded = 0
    var covered = 0
    var bytes = 0L
    for ((region, batch) in layers.groupBy { regionOf(it.key) }) {
      val result = region(region).append(batch)
      written += result.layersWritten
      discarded += result.layersDiscarded
      covered += result.coveredCells
      bytes += result.bytesAdded
    }
    return TileHistoryStore.AppendResult(written, discarded, covered, bytes)
  }

  @Synchronized
  override fun close() {
    open.values.forEach(TileHistoryStore::close)
    open.clear()
  }

  @Synchronized fun openRegionCount(): Int = open.size

  @Synchronized fun regionOpenCount(): Long = opened

  @Synchronized fun regionEvictionCount(): Long = evicted

  @Synchronized fun openIndexArrayBytes(): Long = open.values.sumOf { it.indexArrayBytes }

  private fun region(key: TileKey): TileHistoryStore = region(regionOf(key))

  private fun region(region: Region): TileHistoryStore {
    open[region]?.let {
      return it
    }
    if (open.size >= maxOpenRegions) {
      val eldest = open.entries.iterator().next()
      eldest.value.close()
      open.remove(eldest.key)
      evicted++
    }
    return TileHistoryStore(directory.resolve("${region.x}_${region.z}")).also {
      open[region] = it
      opened++
    }
  }

  private fun regionOf(key: TileKey) =
      Region(Math.floorDiv(key.x, REGION_TILES), Math.floorDiv(key.z, REGION_TILES))

  companion object {
    const val REGION_TILES = 32
  }
}
