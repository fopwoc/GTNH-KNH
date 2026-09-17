package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapPageCache

internal object BenchmarkPageRenderer {
  data class Result(val frame: GpuCanvasFrame, val pageCount: Int, val elapsedNanos: Long)

  fun read(cache: MapPageCache, camera: MapCamera, epoch: Long, latest: Boolean): Result {
    val started = System.nanoTime()
    val draws =
        camera.visiblePages().mapNotNull { key ->
          val page = if (latest) cache.latest(key) else cache.historical(key, epoch)
          page?.let { camera.draw(key, it.image) }
        }
    return Result(GpuCanvasFrame(draws), draws.size, System.nanoTime() - started)
  }
}
