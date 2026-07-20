package io.github.fopwoc.mods.testgui.client.gui.ui.screens.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun ControlsRoute(
    viewModel: ControlsViewModel = viewModel(ControlsViewModel::class)
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    ControlsView(
        state = state,
        onSelectTab = viewModel::selectTab,
        onPresetSelected = viewModel::setPreset,
        onPowerLevelChanged = viewModel::setPowerLevel,
        onAutomationChanged = viewModel::toggleAutomation,
        onPrimaryAction = viewModel::firePrimaryAction,
        onStyledAction = viewModel::fireStyledAction
    )
}

