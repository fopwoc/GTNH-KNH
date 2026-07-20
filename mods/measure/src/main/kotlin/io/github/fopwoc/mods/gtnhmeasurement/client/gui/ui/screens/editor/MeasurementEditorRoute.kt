package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun MeasurementEditorRoute(
    screenWidth: Int,
    screenHeight: Int,
    refreshToken: Int,
    viewModel: MeasurementEditorViewModel = viewModel(MeasurementEditorViewModel::class),
    onClose: () -> Unit
) {
    LaunchedEffect(refreshToken) {
        viewModel.refreshFromRuntime()
    }

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    MeasurementEditorView(
        state = state,
        screenWidth = screenWidth,
        onSelectMode = viewModel::selectMode,
        onDisableMode = viewModel::disableMode,
        onClose = {
            viewModel.noteCloseRequested()
            onClose()
        }
    )
}

