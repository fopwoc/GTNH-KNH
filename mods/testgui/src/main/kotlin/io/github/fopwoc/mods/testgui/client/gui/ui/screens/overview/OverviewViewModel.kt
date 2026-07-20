package io.github.fopwoc.mods.testgui.client.gui.ui.screens.overview

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiFeature
import io.github.fopwoc.mods.testgui.client.gui.ui.testGuiFeatureCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OverviewViewModel : ViewModel() {
    val stateFlow = MutableStateFlow(
        OverviewModel(features = testGuiFeatureCatalog)
    )

    fun onFeatureOpened(feature: TestGuiFeature) {
        stateFlow.update { state ->
            state.copy(
                openCount = state.openCount + 1,
                lastOpenedTitle = feature.title
            )
        }
    }
}

