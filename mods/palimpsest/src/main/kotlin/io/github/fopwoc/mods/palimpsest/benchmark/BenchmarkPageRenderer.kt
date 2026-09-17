package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapPageCache

internal object BenchmarkPageRenderer {
  data class Result(
      val frame: GpuCanvasFrame,
      val pageCount: Int,
      val tileReads: Long,
      val cachedPages: Int,
      val elapsedNanos: Long,
  )

  fun read(
      cache: MapPageCache,
      camera: MapCamera,
      epoch: Long,
      latest: Boolean,
      checkActive: () -> Unit = {},
  ): Result {
    val started = System.nanoTime()
    val readsBefore = cache.tileReadCount()
    val draws =
        camera.visiblePages().mapNotNull { key ->
          checkActive()
          val page =
              if (latest) cache.latest(key, checkActive)
              else cache.historical(key, epoch, checkActive)
          page?.let { camera.draw(key, it.image) }
        }
    return Result(
        GpuCanvasFrame(draws),
        draws.size,
        cache.tileReadCount() - readsBefore,
        if (latest) cache.cachedLatestPages() else cache.cachedHistoricalPages(),
        System.nanoTime() - started,
    )
  }
}
