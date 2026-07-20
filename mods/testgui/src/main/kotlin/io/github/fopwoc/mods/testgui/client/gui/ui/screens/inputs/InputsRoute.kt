package io.github.fopwoc.mods.testgui.client.gui.ui.screens.inputs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun InputsRoute(
    viewModel: InputsViewModel = viewModel(InputsViewModel::class)
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    InputsView(
        state = state,
        onSelectedIndexChange = viewModel::selectIndex,
        onLoadSelection = viewModel::loadSelectionIntoField,
        onCommitDraft = viewModel::commitDraft,
        onRequestFocus = viewModel::requestFocus,
        onClearFocus = viewModel::clearFocus,
        onResetDraft = viewModel::resetDraft
    )
}

