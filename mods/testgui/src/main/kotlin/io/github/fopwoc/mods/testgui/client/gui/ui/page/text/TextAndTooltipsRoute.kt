package io.github.fopwoc.mods.testgui.client.gui.ui.page.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun TextAndTooltipsRoute(
    viewModel: TextAndTooltipsViewModel = viewModel(TextAndTooltipsViewModel::class)
) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()

  TextAndTooltipsView(
      state = state,
      onSelectTab = viewModel::selectTab,
      onCycleAccentPreview = viewModel::cycleAccentPreview,
  )
}
