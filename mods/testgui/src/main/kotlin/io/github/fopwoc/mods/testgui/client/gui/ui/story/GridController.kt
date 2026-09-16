package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import kotlin.math.ceil
import kotlin.math.floor

internal class GridController {
  var centerX by mutableDoubleStateOf(0.0)
  var centerY by mutableDoubleStateOf(0.0)
  var zoom by mutableDoubleStateOf(2.0)

  val level: Int
    get() {
      var result = 0
      while (32 * (1 shl result) * zoom < 24 && result < 5) result++
      return result
    }

  fun reset() {
    centerX = 0.0
    centerY = 0.0
    zoom = 2.0
  }

  fun frame(images: NoiseImages, width: Int, height: Int): GpuCanvasFrame {
    val lod = level
    val worldSpan = 32 * (1 shl lod)
    val screenSpan = (worldSpan * zoom).toFloat()
    val minX = floor((centerX - width / (2 * zoom)) / worldSpan).toInt()
    val maxX = ceil((centerX + width / (2 * zoom)) / worldSpan).toInt() - 1
    val minY = floor((centerY - height / (2 * zoom)) / worldSpan).toInt()
    val maxY = ceil((centerY + height / (2 * zoom)) / worldSpan).toInt() - 1
    val draws = buildList {
      for (y in minY..maxY) for (x in minX..maxX) {
        add(
            GpuImageDraw(
                image = images.image(lod, x, y),
                x = (width / 2.0 + (x.toDouble() * worldSpan - centerX) * zoom).toFloat(),
                y = (height / 2.0 + (y.toDouble() * worldSpan - centerY) * zoom).toFloat(),
                width = screenSpan,
                height = screenSpan,
            )
        )
      }
    }
    return GpuCanvasFrame(draws)
  }
}
