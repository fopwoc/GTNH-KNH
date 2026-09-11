package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementEditorViewModelTest {
  @Test
  fun selectModeInvokesInjectedActionAndRefreshesState() {
    var selectedMode: MeasurementMode? = null
    var snapshot = MeasurementEditorModel(selectedMode = MeasurementMode.DISABLED)
    val viewModel =
        MeasurementEditorViewModel(
            runtimeSnapshotProvider = { snapshot },
            onModeSelected = { mode ->
              selectedMode = mode
              snapshot = snapshot.copy(selectedMode = mode, contextLabel = "enabled")
            },
            onDisableRequested = {
              snapshot =
                  snapshot.copy(selectedMode = MeasurementMode.DISABLED, contextLabel = "off")
            },
        )

    viewModel.selectMode(MeasurementMode.AREA)

    assertEquals(MeasurementMode.AREA, selectedMode)
    assertEquals(MeasurementMode.AREA, viewModel.stateFlow.value.selectedMode)
    assertEquals("enabled", viewModel.stateFlow.value.contextLabel)
  }

  @Test
  fun disableModeUsesInjectedDisableActionAndClearsUiState() {
    var disableCalls = 0
    var snapshot = MeasurementEditorModel(selectedMode = MeasurementMode.LINE)
    val viewModel =
        MeasurementEditorViewModel(
            runtimeSnapshotProvider = { snapshot },
            onModeSelected = { mode -> snapshot = snapshot.copy(selectedMode = mode) },
            onDisableRequested = {
              disableCalls += 1
              snapshot = snapshot.copy(selectedMode = MeasurementMode.DISABLED)
            },
        )

    viewModel.disableMode()

    assertEquals(1, disableCalls)
    assertEquals(MeasurementMode.DISABLED, viewModel.stateFlow.value.selectedMode)
  }

  @Test
  fun selectingAListRowResolvesTheMeasurementId() {
    val entries =
        listOf(
            MeasurementEntry(id = 7, label = "a", selected = false),
            MeasurementEntry(id = 9, label = "b", selected = false),
        )
    var snapshot = MeasurementEditorModel(entries = entries)
    val selectedIds = mutableListOf<Set<Long>>()
    val viewModel =
        MeasurementEditorViewModel(
            runtimeSnapshotProvider = { snapshot },
            onModeSelected = {},
            onDisableRequested = {},
            onSelectionReplaced = { ids ->
              selectedIds += ids
              snapshot = snapshot.copy(entries = entries.map { it.copy(selected = it.id in ids) })
            },
        )

    viewModel.selectEntries(setOf(1, 5))

    assertEquals(listOf(setOf(9L)), selectedIds)
    assertEquals(setOf(1), viewModel.stateFlow.value.selectedEntryIndices)
  }
}
