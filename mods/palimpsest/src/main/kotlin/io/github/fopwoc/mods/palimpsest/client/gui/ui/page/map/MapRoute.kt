package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasState
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
internal fun MapRoute(
    viewModel: MapViewModel,
    screenWidth: Int,
    screenHeight: Int,
    onClose: () -> Unit,
) {
    val canvas = remember { GpuCanvasState(GpuCanvasFrame(emptyList())) }
    val canvasHeight = mapCanvasHeight(screenHeight)
    // Every render frame moves what is gliding and resubmits; frame() is cheap and returns what
    // is ready.
    LaunchedEffect(screenWidth, canvasHeight) {
        while (true) {
            withFrameNanos { nanos ->
                viewModel.advance(nanos, screenWidth, canvasHeight)
                canvas.submit(viewModel.frame(screenWidth, canvasHeight, nanos))
            }
        }
    }

    val model by viewModel.model.collectAsStateWithLifecycle()

    MapView(
        model = model,
        canvas = canvas,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        onOpenHistory = viewModel::openHistory,
        onCloseHistory = viewModel::closeHistory,
        onSelectSnapshot = viewModel::selectSnapshot,
        onHistoryScrolled = viewModel::historyScrolledTo,
        onClose = onClose,
    )
}
