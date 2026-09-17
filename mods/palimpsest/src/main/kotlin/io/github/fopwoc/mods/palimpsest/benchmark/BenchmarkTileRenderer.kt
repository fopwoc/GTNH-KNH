package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.awt.Color

/** Materializes one historical viewport into the framework's GPU image commands. */
internal object BenchmarkTileRenderer {
  const val VIEW_COLUMNS = 8
  const val VIEW_ROWS = 6
  const val DRAW_SIZE = 32
  const val WIDTH = VIEW_COLUMNS * DRAW_SIZE
  const val HEIGHT = VIEW_ROWS * DRAW_SIZE

  data class Result(
      val frame: GpuCanvasFrame,
      val elapsedNanos: Long,
      val visitedLayers: Int,
      val decodedLayers: Int,
      val visibleTiles: Int,
      val tileCosts: List<CheckpointPlanner.TileCost>,
  )

  private val palette =
      IntArray(256) { index ->
        val hue = (index ushr 3) / 32f
        val shade = index and 7
        Color.HSBtoRGB(hue, 0.62f, 0.36f + shade * 0.085f)
      }

  fun read(store: TileHistoryStore, epoch: Long, left: Int, top: Int): Result {
    val start = System.nanoTime()
    val draws = ArrayList<GpuImageDraw>(VIEW_COLUMNS * VIEW_ROWS)
    var visited = 0
    var decoded = 0
    val costs = ArrayList<CheckpointPlanner.TileCost>(VIEW_COLUMNS * VIEW_ROWS)
    for (row in 0 until VIEW_ROWS) for (column in 0 until VIEW_COLUMNS) {
      val key = TileKey(left + column, top + row)
      val tile = store.read(key, epoch) ?: continue
      visited += tile.layersVisited
      decoded += tile.layersDecoded
      costs += CheckpointPlanner.TileCost(key, tile.layersVisited)
      val rgba = ByteArray(TileLayer.PIXELS * 4)
      for (position in 0 until TileLayer.PIXELS) {
        val rgb = palette[tile.colors[position].toInt() and 255]
        val at = position * 4
        rgba[at] = (rgb ushr 16).toByte()
        rgba[at + 1] = (rgb ushr 8).toByte()
        rgba[at + 2] = rgb.toByte()
        rgba[at + 3] = 0xFF.toByte()
      }
      draws +=
          GpuImageDraw(
              GpuImage(TileLayer.SIDE, TileLayer.SIDE, rgba),
              column.toFloat() * DRAW_SIZE,
              row.toFloat() * DRAW_SIZE,
              DRAW_SIZE.toFloat(),
              DRAW_SIZE.toFloat(),
          )
    }
    return Result(
        GpuCanvasFrame(draws),
        System.nanoTime() - start,
        visited,
        decoded,
        draws.size,
        costs,
    )
  }
}
