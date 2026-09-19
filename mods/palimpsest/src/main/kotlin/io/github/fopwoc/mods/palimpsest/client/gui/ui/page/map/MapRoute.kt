package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
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
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.map.MapTime

/** Height of the bar under the map; the canvas and the history strip fill everything above it. */
internal const val BAR_HEIGHT = 22

/**
 * Full-screen map: the canvas fills everything above a one-line bar; the history strip slides in
 * over the canvas' right edge on demand.
 */
@Composable
internal fun MapRoute(
    session: MapSession,
    state: MapViewState,
    history: MapHistoryState,
    screenWidth: Int,
    screenHeight: Int,
    onClose: () -> Unit,
) {
    val canvas = remember { GpuCanvasState(GpuCanvasFrame(emptyList())) }
    val canvasHeight = (screenHeight - BAR_HEIGHT).coerceAtLeast(1)
    // Every render frame eases the camera and the strip, then resubmits; frame() is cheap and
    // returns what is ready.
    LaunchedEffect(screenWidth, canvasHeight) {
        while (true) {
            withFrameNanos { nanos ->
                state.advance(nanos, screenWidth, canvasHeight)
                history.advance(nanos, canvasHeight - HISTORY_HEADER_HEIGHT)
                canvas.submit(
                    session.map.view.frame(state.camera(screenWidth, canvasHeight), history.time)
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            GpuCanvas(
                state = canvas,
                modifier =
                    Modifier.width(screenWidth.uu)
                        .height(canvasHeight.uu)
                        .background(Color(0xFF0B0C12)),
            )
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(BAR_HEIGHT.uu)
                        .background(Color(0xCC15161F))
                        .padding(horizontal = 4.uu),
                horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
                verticalAlignment = VerticalAlignment.CENTER,
            ) {
                val time = history.time
                Text(
                    "${state.centerX.toInt()}, ${state.centerZ.toInt()} · " +
                        zoomLabel(state.pixelsPerBlock)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(if (time is MapTime.At) "At ${formatEpoch(time.epoch)}" else "Live")
                Button("History", enabled = !history.open) { history.open() }
                Button("Close") { onClose() }
            }
        }
        if (history.open) {
            MapHistoryPanel(history, canvasHeight, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

private fun zoomLabel(pixelsPerBlock: Double): String =
    if (pixelsPerBlock >= 1) "${pixelsPerBlock.toInt()} px/block"
    else "1 px = ${(1 / pixelsPerBlock).toInt()} blocks"
