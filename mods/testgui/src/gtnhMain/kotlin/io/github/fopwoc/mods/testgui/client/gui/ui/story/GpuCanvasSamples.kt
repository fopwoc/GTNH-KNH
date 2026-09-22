package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImage
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

@Composable
internal fun WholeImageSample() {
    val image = remember { sampleImage(alpha = 255, blue = false) }
    val frame = remember(image) { GpuCanvasFrame(listOf(GpuImageDraw(image, 0f, 0f, 96f, 64f))) }
    GpuCanvas(
        frame,
        modifier = Modifier.width(96.uu).height(64.uu).background(Color(0xFF11121B)),
    )
}

@Composable
internal fun LayeredImagesSample() {
    val back = remember { sampleImage(alpha = 255, blue = false) }
    val front = remember { sampleImage(alpha = 170, blue = true) }
    val frame =
        remember(back, front) {
            GpuCanvasFrame(
                listOf(
                    GpuImageDraw(back, 0f, 0f, 72f, 72f),
                    GpuImageDraw(front, 28f, 12f, 72f, 72f),
                )
            )
        }
    GpuCanvas(
        frame,
        modifier = Modifier.width(100.uu).height(72.uu).background(Color(0xFF11121B)),
    )
}

private fun sampleImage(alpha: Int, blue: Boolean): GpuImage {
    val bytes = ByteArray(16 * 16 * 4)
    for (y in 0 until 16) for (x in 0 until 16) {
        val at = (y * 16 + x) * 4
        val bright = if ((x / 4 + y / 4) % 2 == 0) 220 else 120
        bytes[at] = (if (blue) 35 else bright).toByte()
        bytes[at + 1] = (if (blue) 105 else 70).toByte()
        bytes[at + 2] = (if (blue) bright else 35).toByte()
        bytes[at + 3] = alpha.toByte()
    }
    return GpuImage(16, 16, bytes)
}
