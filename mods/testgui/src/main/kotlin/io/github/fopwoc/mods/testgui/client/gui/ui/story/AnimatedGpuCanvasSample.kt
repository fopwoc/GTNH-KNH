package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

private enum class UpdateRate {
  FPS_60,
  EVERY_FRAME,
}

@Composable
internal fun AnimatedGpuCanvasSample() {
  val images = remember { AnimatedCanvasImages() }
  val canvas = remember { GpuCanvasState(canvasFrame(images)) }
  var rate by remember { mutableStateOf<UpdateRate?>(null) }
  var updatesPerSecond by remember { mutableIntStateOf(0) }
  var totalUpdates by remember { mutableIntStateOf(0) }

  LaunchedEffect(rate) {
    val runningRate = rate ?: return@LaunchedEffect
    var nextUpdate = 0L
    var windowStart = System.nanoTime()
    var windowUpdates = 0
    var submitted = totalUpdates
    try {
      while (true) {
        val frameTime = withFrameNanos { it }
        if (runningRate == UpdateRate.FPS_60 && frameTime < nextUpdate) continue
        if (runningRate == UpdateRate.FPS_60) {
          nextUpdate =
              if (nextUpdate == 0L) frameTime + FRAME_INTERVAL_NANOS
              else maxOf(nextUpdate + FRAME_INTERVAL_NANOS, frameTime + 1)
        }
        canvas.submit(canvasFrame(images))
        submitted++
        windowUpdates++
        val now = System.nanoTime()
        if (now - windowStart >= 1_000_000_000L) {
          updatesPerSecond = (windowUpdates * 1_000_000_000L / (now - windowStart)).toInt()
          totalUpdates = submitted
          windowUpdates = 0
          windowStart = now
        }
      }
    } finally {
      totalUpdates = submitted
    }
  }

  GpuCanvas(
      state = canvas,
      modifier =
          Modifier.width(CANVAS_WIDTH.uu).height(CANVAS_HEIGHT.uu).background(Color(0xFF11121B)),
  )
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
  ) {
    Button("60 FPS", modifier = Modifier.weight(1f), enabled = rate != UpdateRate.FPS_60) {
      updatesPerSecond = 0
      rate = UpdateRate.FPS_60
    }
    Button("Max FPS", modifier = Modifier.weight(1f), enabled = rate != UpdateRate.EVERY_FRAME) {
      updatesPerSecond = 0
      rate = UpdateRate.EVERY_FRAME
    }
    Button("Stop", modifier = Modifier.weight(1f), enabled = rate != null) {
      rate = null
      updatesPerSecond = 0
    }
  }
  Text("${if (rate == null) "Stopped" else "Running"} · $updatesPerSecond updates/s")
  Text("$totalUpdates images submitted")
  Text("512×512 RGBA · 1 MiB per update")
  Text("Compare game FPS with F3 while stopped and running.")
}

private fun canvasFrame(images: AnimatedCanvasImages): GpuCanvasFrame =
    GpuCanvasFrame(
        listOf(
            GpuImageDraw(
                images.next(),
                0f,
                0f,
                CANVAS_WIDTH.toFloat(),
                CANVAS_HEIGHT.toFloat(),
            )
        )
    )

private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / 60
