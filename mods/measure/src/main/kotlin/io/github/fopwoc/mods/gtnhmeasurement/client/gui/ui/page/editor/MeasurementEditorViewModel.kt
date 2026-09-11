package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.framework.ui.compose.state.TextFieldState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementExchange
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import kotlinx.coroutines.flow.MutableStateFlow

class MeasurementEditorViewModel(
    private val runtimeSnapshotProvider: () -> MeasurementEditorModel,
    private val onModeSelected: (MeasurementMode) -> Unit,
    private val onDisableRequested: () -> Unit,
    private val onSelectionReplaced: (Set<Long>) -> Unit = {},
    private val onDeleteSelected: () -> Unit = {},
    private val onClearSelection: () -> Unit = {},
    private val onUndo: () -> Unit = {},
    private val onRedo: () -> Unit = {},
    private val onExport: (String) -> String = { "" },
    private val onImport: (String) -> String = { "" },
    private val onMoveSelection: () -> String = { "" },
) : ViewModel() {
  val exportName = TextFieldState()

  constructor() :
      this(
          runtimeSnapshotProvider = { MeasurementEditorRuntimeSnapshot.read() },
          onModeSelected = MeasurementSession::switchTo,
          onDisableRequested = {
            MeasurementSession.disable()
            MeasurementSelectionState.clearTransientState()
          },
          onSelectionReplaced = MeasurementSelectionState::replaceSelection,
          onDeleteSelected = { MeasurementSelectionState.deleteSelected() },
          onClearSelection = MeasurementSelectionState::clearSelection,
          onUndo = { MeasurementSelectionState.undo() },
          onRedo = { MeasurementSelectionState.redo() },
          onExport = { MeasurementExchange.export(it) },
          onImport = { MeasurementExchange.import(it) },
          onMoveSelection = { MeasurementExchange.moveSelection() },
      )

  val stateFlow = MutableStateFlow(runtimeSnapshotProvider())

  private var exchangeMessage = ""

  fun refreshFromRuntime() {
    stateFlow.value = runtimeSnapshotProvider().copy(exchangeMessage = exchangeMessage)
  }

  fun export() {
    exchangeMessage = onExport(exportName.text)
    refreshFromRuntime()
  }

  fun import() {
    exchangeMessage = onImport(exportName.text)
    refreshFromRuntime()
  }

  /** Returns true when a move started and the screen should close so the user can aim. */
  fun moveSelection(): Boolean {
    val hadSelection = stateFlow.value.selectedCount > 0
    exchangeMessage = onMoveSelection()
    refreshFromRuntime()
    return hadSelection
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

  fun selectEntries(indices: Set<Int>) {
    val entries = stateFlow.value.entries
    onSelectionReplaced(indices.mapNotNullTo(HashSet()) { entries.getOrNull(it)?.id })
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
