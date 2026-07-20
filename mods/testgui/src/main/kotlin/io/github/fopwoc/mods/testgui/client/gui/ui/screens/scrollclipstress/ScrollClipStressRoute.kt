package io.github.fopwoc.mods.testgui.client.gui.ui.screens.scrollclipstress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun ScrollClipStressRoute(
    viewModel: ScrollClipStressViewModel = viewModel(ScrollClipStressViewModel::class)
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    ScrollClipStressView(
        state = state,
        onAddLane = viewModel::addLane,
        onRemoveLane = viewModel::removeLane,
        onIncreaseBadgeOffset = viewModel::increaseBadgeOffset,
        onDecreaseBadgeOffset = viewModel::decreaseBadgeOffset,
        onCompactModeChange = viewModel::toggleCompactMode,
        onAlternatingBadgesChange = viewModel::toggleAlternatingBadges
    )
}

