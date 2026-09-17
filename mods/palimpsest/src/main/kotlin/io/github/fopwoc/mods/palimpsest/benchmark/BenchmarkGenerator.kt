package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.Random

/** Reproducible edits and explicit checkpoints over a fixed synthetic 32×32 tile world. */
internal object BenchmarkGenerator {
  enum class Pattern {
    SPARSE,
    MIXED,
  }

  const val WORLD_SIDE = 32
  const val EPOCHS_PER_BATCH = 250
  private const val CHANGED_TILES_PER_EPOCH = 16
  private const val CHANGED_CELLS_PER_TILE = 8

  data class Result(
      val layersWritten: Int,
      val coveredCells: Int,
      val elapsedNanos: Long,
      val bytesAdded: Long,
  )

  fun append(store: TileHistoryStore, pattern: Pattern = Pattern.SPARSE): Result {
    val started = System.nanoTime()
    val previousBytes = store.byteCount
    val layers = ArrayList<TileLayer>()
    val tiles = HashMap<TileKey, ByteArray>(WORLD_SIDE * WORLD_SIDE)
    if (store.tileCount == 0) {
      for (z in 0 until WORLD_SIDE) for (x in 0 until WORLD_SIDE) {
        val key = TileKey(x, z)
        val colors =
            ByteArray(TileLayer.PIXELS) { position ->
              val px = position % TileLayer.SIDE
              val pz = position / TileLayer.SIDE
              ((x * 7 + z * 11 + px / 4 * 3 + pz / 4 * 5) and 255).toByte()
            }
        tiles[key] = colors
        layers += TileLayer.complete(key, 0, colors)
      }
    } else {
      for (z in 0 until WORLD_SIDE) for (x in 0 until WORLD_SIDE) {
        val key = TileKey(x, z)
        tiles[key] = checkNotNull(store.read(key, store.latestEpoch)).colors
      }
    }

    val firstEpoch = store.latestEpoch + 1
    for (epoch in firstEpoch until firstEpoch + EPOCHS_PER_BATCH) {
      val random = Random(0x50414C49L xor epoch)
      val changedTiles = HashSet<TileKey>()
      while (changedTiles.size < CHANGED_TILES_PER_EPOCH) {
        changedTiles += TileKey(random.nextInt(WORLD_SIDE), random.nextInt(WORLD_SIDE))
      }
      for (key in changedTiles.sortedWith(compareBy(TileKey::z, TileKey::x))) {
        val old = checkNotNull(tiles[key])
        val next = old.copyOf()
        val positions =
            when (pattern) {
              Pattern.SPARSE -> sparsePositions(random)
              Pattern.MIXED -> mixedPositions(random)
            }
        for (position in positions) {
          val range = if (pattern == Pattern.SPARSE) 48 else 255
          next[position] = ((next[position].toInt() and 255) + 1 + random.nextInt(range)).toByte()
        }
        layers += checkNotNull(TileLayer.changed(key, epoch, old, next))
        tiles[key] = next
      }
    }
    store.append(layers)
    return Result(
        layers.size,
        layers.sumOf { it.colors.size },
        System.nanoTime() - started,
        store.byteCount - previousBytes,
    )
  }

  fun checkpoint(store: TileHistoryStore): Result {
    require(store.tileCount == WORLD_SIDE * WORLD_SIDE) { "Generate a tile world first" }
    val started = System.nanoTime()
    val previousBytes = store.byteCount
    val epoch = store.latestEpoch + 1
    val layers = buildList {
      for (z in 0 until WORLD_SIDE) for (x in 0 until WORLD_SIDE) {
        val key = TileKey(x, z)
        val colors = checkNotNull(store.read(key, store.latestEpoch)).colors
        add(TileLayer.complete(key, epoch, colors))
      }
    }
    store.append(layers)
    return Result(
        layers.size,
        layers.sumOf { it.colors.size },
        System.nanoTime() - started,
        store.byteCount - previousBytes,
    )
  }

  private fun sparsePositions(random: Random): Set<Int> = buildSet {
    while (size < CHANGED_CELLS_PER_TILE) add(random.nextInt(TileLayer.PIXELS))
  }

  private fun mixedPositions(random: Random): Set<Int> = buildSet {
    if (random.nextInt(32) == 0) {
      addAll(0 until TileLayer.PIXELS)
      return@buildSet
    }
    repeat(2 + random.nextInt(4)) {
      val width = 2 + random.nextInt(11)
      val height = 2 + random.nextInt(11)
      val left = random.nextInt(TileLayer.SIDE - width + 1)
      val top = random.nextInt(TileLayer.SIDE - height + 1)
      for (y in top until top + height) for (x in left until left + width) {
        add(y * TileLayer.SIDE + x)
      }
    }
    repeat(8 + random.nextInt(49)) { add(random.nextInt(TileLayer.PIXELS)) }
  }
}
