package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.vanilla.Button
import io.github.fopwoc.mods.framework.ui.compose.foundation.Box
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Spacer
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.Alignment
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.VerticalAlignment
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.component.MapHistoryStrip
import io.github.fopwoc.mods.palimpsest.map.MapTime

/** Height of the bar under the map; the canvas and the history strip fill everything above it. */
internal const val MAP_BAR_HEIGHT = 22

/** Width of the history strip along the canvas' right edge. */
internal const val MAP_HISTORY_WIDTH = 112

internal fun mapCanvasHeight(screenHeight: Int): Int =
    (screenHeight - MAP_BAR_HEIGHT).coerceAtLeast(1)

/**
 * Full-screen map: the canvas, with entity dots and the player's arrow over it, fills everything
 * above a one-line bar; the history strip sits over the canvas' right edge while open.
 */
@Composable
internal fun MapView(
    model: MapModel,
    canvas: GpuCanvasState,
    dots: GpuCanvasState,
    marker: GpuCanvasState,
    screenWidth: Int,
    screenHeight: Int,
    onOpenHistory: () -> Unit = {},
    onCloseHistory: () -> Unit = {},
    onSelectSnapshot: (Int) -> Unit = {},
    onHistoryScrolled: (Double) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val canvasHeight = mapCanvasHeight(screenHeight)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(screenWidth.uu).height(canvasHeight.uu)) {
                GpuCanvas(
                    state = canvas,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0C12)),
                )
                GpuCanvas(state = dots, modifier = Modifier.fillMaxSize())
                GpuCanvas(state = marker, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(MAP_BAR_HEIGHT.uu)
                        .background(Color(0xCC15161F))
                        .padding(horizontal = 4.uu),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
                val time = model.time
                Text(
                    "${model.centerX.toInt()}, ${model.centerZ.toInt()} · " +
                        zoomLabel(model.pixelsPerBlock)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(if (time is MapTime.At) "At ${formatEpoch(time.epoch)}" else "Live")
                Button("History", enabled = model.history == null) { onOpenHistory() }
                Button("Close") { onClose() }
            }
        }
        model.history?.let { history ->
            MapHistoryStrip(
                model = history,
                width = MAP_HISTORY_WIDTH,
                height = canvasHeight,
                modifier = Modifier.align(Alignment.TopEnd),
                onSelect = onSelectSnapshot,
                onScrolled = onHistoryScrolled,
                onClose = onCloseHistory,
            )
        }
    }
}

private fun zoomLabel(pixelsPerBlock: Double): String =
    if (pixelsPerBlock >= 1) "${pixelsPerBlock.toInt()} px/block"
    else "1 px = ${(1 / pixelsPerBlock).toInt()} blocks"
