package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageCache
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.nio.file.Path

/** A wide, isolated world that exercises region reopening and one-pixel-per-tile LOD reads. */
internal object WideWorldLoadScenario {
  data class Result(
      val tiles: Int,
      val diskBytes: Long,
      val coldPageNanos: Long,
      val tileLookups: Long,
      val recordBytesRead: Long,
      val warmPageNanos: Long,
      val historicalPageNanos: Long,
      val openRegions: Int,
  )

  fun run(
      directory: Path,
      side: Int,
      shouldStop: () -> Boolean,
      onProgress: (String) -> Unit,
  ): Result? {
    require(side >= MapPageKey.SIDE && side % RegionTileHistoryStore.REGION_TILES == 0)
    val regionSide = RegionTileHistoryStore.REGION_TILES
    RegionTileHistoryStore(directory, maxOpenRegions = 16).use { store ->
      for (regionZ in 0 until side / regionSide) for (regionX in 0 until side / regionSide) {
        if (shouldStop()) return null
        val layers = ArrayList<TileLayer>(regionSide * regionSide)
        for (localZ in 0 until regionSide) for (localX in 0 until regionSide) {
          val x = regionX * regionSide + localX
          val z = regionZ * regionSide + localZ
          layers += TileLayer.full(TileKey(x, z), 0, tileColors(x, z))
        }
        check(store.append(layers).layersWritten == layers.size)
        onProgress(
            "Wide world: ${(regionZ * side / regionSide + regionX + 1) * 1024}/${side * side} tiles"
        )
      }
      val key = TileKey(0, 0)
      val mask = LongArray(TileLayer.MASK_WORDS)
      mask[2] = 1L shl 8
      check(store.append(listOf(TileLayer(key, 1, mask, byteArrayOf(99)))).layersWritten == 1)
    }
    if (shouldStop()) return null
    val diskBytes =
        Files.walk(directory).use { paths ->
          paths.filter(Files::isRegularFile).mapToLong(Files::size).sum()
        }
    RegionTileHistoryStore(directory, maxOpenRegions = 16).use { store ->
      val palette = IntArray(256) { it * 0x010101 }
      var recordBytes = 0L
      val cache =
          MapPageCache(
              { key, epoch -> store.read(key, epoch)?.colors },
              palette,
              hasChanged = store::hasChanges,
              readSamples = { key, epoch, positions ->
                store
                    .readSamples(key, epoch, positions)
                    ?.also { recordBytes += it.bytesRead }
                    ?.colors
              },
          )
      val pageKey = MapPageKey(0, 0, MapPageKey.MAX_LOD)
      val start = System.nanoTime()
      val latest = checkNotNull(cache.latest(pageKey))
      val coldNanos = System.nanoTime() - start
      val tileLookups = cache.tileReadCount()
      check(tileLookups == 16_384L)
      check(latest.colorAt(0, 0) == 0xFF636363.toInt())
      check(latest.colorAt(1, 0) == gray(tileColors(1, 0)[136]))
      val warmStart = System.nanoTime()
      check(cache.latest(pageKey) === latest)
      val warmNanos = System.nanoTime() - warmStart
      check(cache.tileReadCount() == tileLookups)
      if (shouldStop()) return null
      val historicalStart = System.nanoTime()
      val old = checkNotNull(cache.historical(pageKey, 0))
      val historicalNanos = System.nanoTime() - historicalStart
      check(old.colorAt(0, 0) == gray(tileColors(0, 0)[136]))
      check(old.colorAt(1, 0) == latest.colorAt(1, 0))
      return Result(
          side * side,
          diskBytes,
          coldNanos,
          tileLookups,
          recordBytes,
          warmNanos,
          historicalNanos,
          store.openRegionCount(),
      )
    }
  }

  private fun tileColors(x: Int, z: Int) =
      ByteArray(TileLayer.PIXELS) { ((x * 31 + z * 17 + it) and 255).toByte() }

  private fun gray(value: Byte): Int {
    val color = value.toInt() and 255
    return 0xFF000000.toInt() or (color shl 16) or (color shl 8) or color
  }
}
