package io.github.fopwoc.mods.testgui.client.gui.ui.page.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiFeature

@Composable
fun OverviewRoute(
    featureCatalog: List<TestGuiFeature>,
    onOpenFeature: (TestGuiFeature) -> Unit,
    viewModel: OverviewViewModel = viewModel { OverviewViewModel() },
) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()

  OverviewView(
      state = state.copy(features = featureCatalog),
      onOpenFeature = { feature ->
        viewModel.onFeatureOpened(feature)
        onOpenFeature(feature)
      },
  )
}
