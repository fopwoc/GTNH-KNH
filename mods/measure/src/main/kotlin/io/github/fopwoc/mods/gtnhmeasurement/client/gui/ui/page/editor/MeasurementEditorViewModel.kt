package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import kotlinx.coroutines.flow.MutableStateFlow

class MeasurementEditorViewModel(
    private val runtimeSnapshotProvider: () -> MeasurementEditorModel,
    private val onModeSelected: (MeasurementMode) -> Unit,
    private val onDisableRequested: () -> Unit,
    private val onEntrySelected: (Long) -> Unit = {},
    private val onDeleteSelected: () -> Unit = {},
    private val onClearSelection: () -> Unit = {},
    private val onUndo: () -> Unit = {},
    private val onRedo: () -> Unit = {},
) : ViewModel() {
  constructor() :
      this(
          runtimeSnapshotProvider = { MeasurementEditorRuntimeSnapshot.read() },
          onModeSelected = MeasurementSession::switchTo,
          onDisableRequested = {
            MeasurementSession.disable()
            MeasurementSelectionState.clearTransientState()
          },
          onEntrySelected = MeasurementSelectionState::selectOnly,
          onDeleteSelected = { MeasurementSelectionState.deleteSelected() },
          onClearSelection = MeasurementSelectionState::clearSelection,
          onUndo = { MeasurementSelectionState.undo() },
          onRedo = { MeasurementSelectionState.redo() },
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

  fun selectEntry(index: Int) {
    stateFlow.value.entries.getOrNull(index)?.let { onEntrySelected(it.id) }
    refreshFromRuntime()
  }

  fun deleteSelected() = act(onDeleteSelected)

  fun clearSelection() = act(onClearSelection)

  fun undo() = act(onUndo)

  fun redo() = act(onRedo)

  private fun act(action: () -> Unit) {
    action()
    refreshFromRuntime()
  }
}
