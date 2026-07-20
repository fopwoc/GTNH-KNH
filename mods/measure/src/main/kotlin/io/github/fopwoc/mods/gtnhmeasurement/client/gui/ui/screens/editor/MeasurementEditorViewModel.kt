package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.screens.editor

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import kotlinx.coroutines.flow.MutableStateFlow

class MeasurementEditorViewModel(
    private val runtimeSnapshotProvider: () -> MeasurementEditorModel,
    private val onModeSelected: (MeasurementMode) -> Unit,
    private val onDisableRequested: () -> Unit
) : ViewModel() {
    constructor() : this(
        runtimeSnapshotProvider = { MeasurementEditorRuntimeSnapshot.read() },
        onModeSelected = MeasurementSession::switchTo,
        onDisableRequested = {
            MeasurementSession.disable()
            MeasurementSelectionState.clearTransientState()
        }
    )

    val stateFlow = MutableStateFlow(runtimeSnapshotProvider())

    fun refreshFromRuntime() {
        stateFlow.value = runtimeSnapshotProvider()
    }

    fun selectMode(mode: MeasurementMode) {
        if (mode.isEnabled) {
            onModeSelected(mode)
        } else {
            onDisableRequested()
        }
        refreshFromRuntime()
    }

    fun disableMode() {
        onDisableRequested()
        refreshFromRuntime()
    }

    fun noteCloseRequested() {
        // No-op: close feedback is handled by the host screen, and the compact dialog no longer renders a status line.
    }
}

