package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
              snapshot =
                  snapshot.copy(
                      selectedMode = mode,
                      modeBadgeText = "Mode · ${mode.displayName}",
                      footerText = "enabled",
                  )
            },
            onDisableRequested = {
              snapshot =
                  snapshot.copy(
                      selectedMode = MeasurementMode.DISABLED,
                      modeBadgeText = "Mode · Disabled",
                      footerText = "disabled",
                  )
            },
        )

    viewModel.selectMode(MeasurementMode.AREA)

    assertEquals(MeasurementMode.AREA, selectedMode)
    assertEquals(MeasurementMode.AREA, viewModel.stateFlow.value.selectedMode)
    assertEquals("Mode · Area", viewModel.stateFlow.value.modeBadgeText)
    assertEquals("enabled", viewModel.stateFlow.value.footerText)
  }

  @Test
  fun disableModeUsesInjectedDisableActionAndClearsUiState() {
    var disableCalls = 0
    var snapshot =
        MeasurementEditorModel(
            selectedMode = MeasurementMode.LINE,
            modeBadgeText = "Mode · Line",
            footerText = "enabled",
        )
    val viewModel =
        MeasurementEditorViewModel(
            runtimeSnapshotProvider = { snapshot },
            onModeSelected = { mode ->
              snapshot = snapshot.copy(selectedMode = mode)
            },
            onDisableRequested = {
              disableCalls += 1
              snapshot =
                  snapshot.copy(
                      selectedMode = MeasurementMode.DISABLED,
                      modeBadgeText = "Mode · Disabled",
                      footerText = "disabled",
                  )
            },
        )

    viewModel.disableMode()

    assertEquals(1, disableCalls)
    assertEquals(MeasurementMode.DISABLED, viewModel.stateFlow.value.selectedMode)
    assertEquals("disabled", viewModel.stateFlow.value.footerText)
  }

  @Test
  fun noteCloseRequestedLeavesPresentationModelUnchanged() {
    val viewModel =
        MeasurementEditorViewModel(
            runtimeSnapshotProvider = {
              MeasurementEditorModel(
                  selectedMode = MeasurementMode.LINE,
                  modeBadgeText = "Mode · Line",
              )
            },
            onModeSelected = {},
            onDisableRequested = {},
        )

    viewModel.noteCloseRequested()

    assertEquals(MeasurementMode.LINE, viewModel.stateFlow.value.selectedMode)
    assertEquals("Mode · Line", viewModel.stateFlow.value.modeBadgeText)
    assertTrue(viewModel.stateFlow.value.availableModes.contains(MeasurementMode.AREA))
  }
}
