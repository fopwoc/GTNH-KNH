package io.github.fopwoc.mods.framework.ui.compose.minecraft.render

import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.layout.core.Rect
import java.util.IdentityHashMap

/** Keeps one GL image cache per canvas node and releases caches that leave composition. */
internal class GpuCanvasRenderer {
  private data class Entry(val renderer: GpuImageRenderer, var lastFrame: Int)

  private val canvases = IdentityHashMap<Any, Entry>()
  private var frame = 0

  fun beginFrame() {
    frame++
  }

  fun draw(bounds: Rect, width: Int, height: Int, imageFrame: GpuCanvasFrame, handle: Any) {
    val entry = canvases[handle] ?: Entry(GpuImageRenderer(), frame).also { canvases[handle] = it }
    entry.lastFrame = frame
    entry.renderer.draw(bounds, width, height, imageFrame)
  }

  fun endFrame() {
    val iterator = canvases.values.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (entry.lastFrame == frame) continue
      entry.renderer.dispose()
      iterator.remove()
    }
  }

  fun dispose() {
    canvases.values.forEach { it.renderer.dispose() }
    canvases.clear()
  }
}
