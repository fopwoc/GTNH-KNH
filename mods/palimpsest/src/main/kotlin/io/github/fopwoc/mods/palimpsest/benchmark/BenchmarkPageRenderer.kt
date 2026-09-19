package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.palimpsest.map.MapCamera

internal object BenchmarkPageRenderer {
    data class Result(
        val frame: GpuCanvasFrame,
        val pageCount: Int,
        val nodesRead: Long,
        val tilesDecoded: Long,
        val cachedPages: Int,
        val elapsedNanos: Long,
    )

    fun read(world: BenchmarkWorld, camera: MapCamera, epoch: Long, latest: Boolean, checkActive: () -> Unit = {}): Result {
        val started = System.nanoTime()
        val nodesBefore = world.tree.nodesRead()
        val decodedBefore = world.tree.tilesDecoded()
        val draws =
            camera.visiblePages().mapNotNull { key ->
                checkActive()
                val page = if (latest) world.pages.latest(key, checkActive) else world.pages.historical(key, epoch, checkActive)
                page?.let { camera.draw(key, it.image) }
            }
        return Result(
            GpuCanvasFrame(draws),
            draws.size,
            world.tree.nodesRead() - nodesBefore,
            world.tree.tilesDecoded() - decodedBefore,
            if (latest) world.pages.cachedLatestPages() else world.pages.cachedHistoricalPages(),
            System.nanoTime() - started,
        )
    }
}
