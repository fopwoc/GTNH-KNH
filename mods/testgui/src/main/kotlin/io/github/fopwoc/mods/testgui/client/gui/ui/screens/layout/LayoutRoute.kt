package io.github.fopwoc.mods.testgui.client.gui.ui.screens.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun LayoutRoute(
    viewModel: LayoutViewModel = viewModel(LayoutViewModel::class)
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LayoutView(
        state = state,
        onSelectTab = viewModel::selectTab,
        onAlignmentSelected = viewModel::setAlignmentPreset,
        onAddScrollChip = viewModel::addScrollChip,
        onRemoveScrollChip = viewModel::removeScrollChip
    )
}

