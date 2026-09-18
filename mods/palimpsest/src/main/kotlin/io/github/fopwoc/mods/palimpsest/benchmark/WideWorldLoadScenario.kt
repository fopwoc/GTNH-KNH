package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.map.MapPageCache
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.nio.file.Path

/** Measures direct sampling over a dense world and sparse distant observations. */
internal object WideWorldLoadScenario {
  data class Level(
      val lod: Int,
      val coveredTiles: Long,
      val tileLookups: Long,
      val presentSamples: Int,
      val coldPageNanos: Long,
      val warmPageNanos: Long,
      val historicalPageNanos: Long,
      val logicalRecordBytes: Long,
      val openRegions: Int,
      val regionOpens: Long,
      val regionEvictions: Long,
  )

  data class Result(val tiles: Int, val diskBytes: Long, val levels: List<Level>)

  fun run(
      directory: Path,
      side: Int,
      shouldStop: () -> Boolean,
      onProgress: (String) -> Unit,
  ): Result? {
    require(side >= MapPageKey.SIDE && side % RegionTileHistoryStore.REGION_TILES == 0)
    val regionSide = RegionTileHistoryStore.REGION_TILES
    var writtenTiles = 0
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
        writtenTiles += layers.size
        onProgress("Wide world: $writtenTiles/${side * side} dense tiles")
      }
      val distant = HashSet<TileKey>()
      for (lod in 7..MapPageKey.MAX_LOD) {
        val cellSide = (1 shl (lod - 4)).coerceAtMost(8)
        val stride = 1 shl (lod - 4)
        for (z in 0 until MapPageKey.SIDE step cellSide) {
          for (x in 0 until MapPageKey.SIDE step cellSide) {
            val key =
                TileKey(
                    x * stride,
                    z * stride,
                )
            if (key.x >= side || key.z >= side) distant += key
          }
        }
      }
      for (batch in
          distant
              .groupBy { Math.floorDiv(it.x, regionSide) to Math.floorDiv(it.z, regionSide) }
              .values) {
        if (shouldStop()) return null
        val layers = batch.map { TileLayer.full(it, 0, tileColors(it.x, it.z)) }
        check(store.append(layers).layersWritten == layers.size)
        writtenTiles += layers.size
      }
      onProgress("Wide world: $writtenTiles tiles including distant samples")
      val mask = LongArray(TileLayer.MASK_WORDS)
      mask[2] = 1L shl 8
      check(
          store.append(listOf(TileLayer(TileKey(0, 0), 1, mask, byteArrayOf(99)))).layersWritten ==
              1
      )
    }
    if (shouldStop()) return null
    val diskBytes =
        Files.walk(directory).use { paths ->
          paths.filter(Files::isRegularFile).mapToLong(Files::size).sum()
        }
    RegionTileHistoryStore(directory, maxOpenRegions = 16).use { store ->
      val levels = ArrayList<Level>()
      val palette = IntArray(256) { it * 0x010101 }
      for (lod in 4..MapPageKey.MAX_LOD) {
        if (shouldStop()) return null
        onProgress("Wide world: reading LOD $lod/${MapPageKey.MAX_LOD}")
        var logicalBytes = 0L
        var presentSamples = 0
        val cache =
            MapPageCache(
                { key, epoch -> store.read(key, epoch)?.colors },
                palette,
                hasChanged = store::hasChanges,
                readSamples = { key, epoch, positions ->
                  store
                      .readSamples(key, epoch, positions)
                      ?.also {
                        logicalBytes += it.bytesRead
                        presentSamples++
                      }
                      ?.colors
                },
            )
        val pageKey = MapPageKey(0, 0, lod)
        val opensBefore = store.regionOpenCount()
        val evictionsBefore = store.regionEvictionCount()
        val start = System.nanoTime()
        val latest = checkNotNull(cache.latest(pageKey))
        val coldNanos = System.nanoTime() - start
        val lookups = cache.tileReadCount()
        val latestPresent = presentSamples
        val cellSide = if (lod <= 4) 1 else (1 shl (lod - 4)).coerceAtMost(8)
        val expectedLookups = (MapPageKey.SIDE / cellSide).toLong() * (MapPageKey.SIDE / cellSide)
        check(lookups == expectedLookups)
        check(latestPresent.toLong() == lookups)
        val warmStart = System.nanoTime()
        check(cache.latest(pageKey) === latest)
        val warmNanos = System.nanoTime() - warmStart
        check(cache.tileReadCount() == lookups)
        val historyStart = System.nanoTime()
        val historical = checkNotNull(cache.historical(pageKey, 0))
        val historicalNanos = System.nanoTime() - historyStart
        check(latest.colorAt(0, 0) == 0xFF636363.toInt())
        check(historical.colorAt(0, 0) == gray(tileColors(0, 0)[136]))
        val pageTileSide = MapPageKey.BASE_TILES.toLong() shl lod
        levels +=
            Level(
                lod,
                pageTileSide * pageTileSide,
                lookups,
                latestPresent,
                coldNanos,
                warmNanos,
                historicalNanos,
                logicalBytes,
                store.openRegionCount(),
                store.regionOpenCount() - opensBefore,
                store.regionEvictionCount() - evictionsBefore,
            )
      }
      return Result(writtenTiles, diskBytes, levels)
    }
  }

  private fun tileColors(x: Int, z: Int) =
      ByteArray(TileLayer.PIXELS) { ((x * 31 + z * 17 + it) and 255).toByte() }

  private fun gray(value: Byte): Int {
    val color = value.toInt() and 255
    return 0xFF000000.toInt() or (color shl 16) or (color shl 8) or color
  }
}
