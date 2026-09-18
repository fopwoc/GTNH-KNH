package io.github.fopwoc.mods.testgui.client.gui.ui.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu

internal const val CANVAS_WIDTH = 300
internal const val CANVAS_HEIGHT = 150

@Composable
fun GpuCanvasStory() {
    val controller = rememberGridController()
    val images = remember { NoiseImages() }
    val frame = controller.frame(images, CANVAS_WIDTH, CANVAS_HEIGHT)
    Examples {
        Example("One 16×16 RGBA image stretched to 96×64 without a CPU-sized frame.") {
            WholeImageSample()
        }
        Example("Two overlapping images with alpha blending and clipping.") {
            LayeredImagesSample()
        }
        Example("Compose selects cells and LOD; GPU canvas only draws the resulting image quads.") {
            GpuCanvas(
                frame = frame,
                modifier =
                    Modifier.width(CANVAS_WIDTH.uu)
                        .height(CANVAS_HEIGHT.uu)
                        .background(Color(0xFF11121B)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
            ) {
                Button("←", modifier = Modifier.weight(1f)) {
                    controller.centerX -= 16 / controller.zoom
                }
                Button("→", modifier = Modifier.weight(1f)) {
                    controller.centerX += 16 / controller.zoom
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
            ) {
                Button("↑", modifier = Modifier.weight(1f)) {
                    controller.centerY -= 16 / controller.zoom
                }
                Button("↓", modifier = Modifier.weight(1f)) {
                    controller.centerY += 16 / controller.zoom
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = HorizontalArrangement.spacedBy(3.uu),
            ) {
                Button("−", modifier = Modifier.weight(1f)) {
                    controller.zoom = (controller.zoom / 1.5).coerceAtLeast(0.125)
                }
                Button("+", modifier = Modifier.weight(1f)) {
                    controller.zoom = (controller.zoom * 1.5).coerceAtMost(8.0)
                }
                Button("Reset", modifier = Modifier.weight(1f)) { controller.reset() }
            }
            Text("LOD ${controller.level} · ${frame.draws.size} visible images")
        }
        Example("Animated image uploads: start, compare game FPS, then stop.") {
            AnimatedGpuCanvasSample()
        }
    }
}

@Composable private fun rememberGridController(): GridController = remember { GridController() }
