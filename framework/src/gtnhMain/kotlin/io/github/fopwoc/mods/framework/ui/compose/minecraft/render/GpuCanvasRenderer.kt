package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import io.github.fopwoc.mods.framework.ui.compose.layout.render.GpuCanvasCache

/** Keeps one GL image cache per canvas node and releases caches that leave composition. */
internal class GpuCanvasRenderer {
    private val cache = GpuCanvasCache(::GpuImageRenderer, GpuImageRenderer::dispose)

    fun beginFrame() = cache.beginFrame()

    fun draw(bounds: Rect, width: Int, height: Int, imageFrame: GpuCanvasFrame, handle: Any) =
        cache.renderer(handle).draw(bounds, width, height, imageFrame)

    fun endFrame() = cache.endFrame()

    fun dispose() = cache.dispose()
}
