package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord

/** Materializes one historical viewport of raw tiles into the framework's GPU image commands. */
internal object BenchmarkTileRenderer {
    const val VIEW_COLUMNS = 8
    const val VIEW_ROWS = 6
    const val DRAW_SIZE = 32
    const val WIDTH = VIEW_COLUMNS * DRAW_SIZE
    const val HEIGHT = VIEW_ROWS * DRAW_SIZE

    data class Result(
        val frame: GpuCanvasFrame,
        val elapsedNanos: Long,
        val nodesRead: Long,
        val tilesDecoded: Long,
        val visibleTiles: Int,
    )

    fun read(world: BenchmarkWorld, epoch: Long, left: Int, top: Int): Result {
        val start = System.nanoTime()
        val nodesBefore = world.tree.nodesRead()
        val decodedBefore = world.tree.tilesDecoded()
        val draws = ArrayList<GpuImageDraw>(VIEW_COLUMNS * VIEW_ROWS)
        for (row in 0 until VIEW_ROWS) for (column in 0 until VIEW_COLUMNS) {
            val tile = world.tree.tile(TileKey(left + column, top + row), epoch) ?: continue
            val rgba = ByteArray(TileRecord.PIXELS * 4)
            for (position in 0 until TileRecord.PIXELS) {
                val rgb = BenchmarkWorld.palette[tile.block(position) and 255]
                val at = position * 4
                rgba[at] = (rgb ushr 16).toByte()
                rgba[at + 1] = (rgb ushr 8).toByte()
                rgba[at + 2] = rgb.toByte()
                rgba[at + 3] = 0xFF.toByte()
            }
            draws +=
                GpuImageDraw(
                    GpuImage(TileRecord.SIDE, TileRecord.SIDE, rgba),
                    column.toFloat() * DRAW_SIZE,
                    row.toFloat() * DRAW_SIZE,
                    DRAW_SIZE.toFloat(),
                    DRAW_SIZE.toFloat(),
                )
        }
        return Result(
            GpuCanvasFrame(draws),
            System.nanoTime() - start,
            world.tree.nodesRead() - nodesBefore,
            world.tree.tilesDecoded() - decodedBefore,
            draws.size,
        )
    }
}
