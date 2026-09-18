package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.component.native.Button
import io.github.fopwoc.mods.framework.ui.compose.component.native.Slider
import io.github.fopwoc.mods.framework.ui.compose.foundation.Column
import io.github.fopwoc.mods.framework.ui.compose.foundation.GpuCanvas
import io.github.fopwoc.mods.framework.ui.compose.foundation.Row
import io.github.fopwoc.mods.framework.ui.compose.foundation.Text
import io.github.fopwoc.mods.framework.ui.compose.model.alignment.HorizontalArrangement
import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import io.github.fopwoc.mods.framework.ui.compose.model.modifier.Modifier
import io.github.fopwoc.mods.framework.ui.compose.unit.uu
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.map.MapTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-screen map: the canvas fills everything above a one-line bar with the time slider. */
@Composable
internal fun MapRoute(
    session: MapSession,
    state: MapViewState,
    screenWidth: Int,
    screenHeight: Int,
    refreshToken: Int,
    onClose: () -> Unit,
) {
    val canvas = remember { GpuCanvasState(GpuCanvasFrame(emptyList())) }
    val canvasHeight = (screenHeight - BAR_HEIGHT).coerceAtLeast(1)
    val now = System.currentTimeMillis()
    val created = session.map.createdEpoch.coerceAtMost(now - 1)
    val time = state.time
    // The screen bumps refreshToken every tick; frame() is cheap and returns whatever is ready.
    LaunchedEffect(refreshToken, state.centerX, state.centerZ, state.pixelsPerBlock, time) {
        canvas.submit(session.map.view.frame(state.camera(screenWidth, canvasHeight), time))
    }
    Column(modifier = Modifier.fillMaxSize()) {
        GpuCanvas(
            state = canvas,
            modifier =
                Modifier.width(screenWidth.uu)
                    .height(canvasHeight.uu)
                    .background(Color(0xFF0B0C12)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT.uu).background(Color(0xCC15161F)),
            horizontalArrangement = HorizontalArrangement.spacedBy(4.uu),
        ) {
            val sliderValue = (time as? MapTime.At)?.epoch?.toDouble() ?: now.toDouble()
            Slider(
                value = sliderValue.coerceIn(created.toDouble(), now.toDouble()),
                onValueChange = { value ->
                    state.time =
                        if (value >= now - LIVE_SNAP_MILLIS) MapTime.Live
                        else MapTime.At(value.toLong())
                },
                valueRange = created.toDouble()..now.toDouble(),
                label =
                    if (time is MapTime.Live) "Live"
                    else "At ${format((time as MapTime.At).epoch)}",
                showDecimal = false,
                modifier = Modifier.weight(1f),
            )
            Button("Live", enabled = time !is MapTime.Live) { state.time = MapTime.Live }
            Text(
                "${state.centerX.toInt()}, ${state.centerZ.toInt()} · ${zoomLabel(state.pixelsPerBlock)}"
            )
            Button("Close") { onClose() }
        }
    }
}

private const val BAR_HEIGHT = 22
private const val LIVE_SNAP_MILLIS = 60_000L
private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun format(epoch: Long): String = formatter.format(Instant.ofEpochMilli(epoch))

private fun zoomLabel(pixelsPerBlock: Double): String =
    if (pixelsPerBlock >= 1) "${pixelsPerBlock.toInt()} px/block"
    else "1 px = ${(1 / pixelsPerBlock).toInt()} blocks"
