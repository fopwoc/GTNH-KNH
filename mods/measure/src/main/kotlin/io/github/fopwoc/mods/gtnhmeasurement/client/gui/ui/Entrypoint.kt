package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui

import androidx.compose.runtime.Composable
import io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.screens.editor.MeasurementEditorRoute

@Composable
fun Entrypoint(
    screenWidth: Int,
    screenHeight: Int,
    refreshToken: Int,
    onClose: () -> Unit
) {
    MeasurementEditorRoute(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        refreshToken = refreshToken,
        onClose = onClose
    )
}

