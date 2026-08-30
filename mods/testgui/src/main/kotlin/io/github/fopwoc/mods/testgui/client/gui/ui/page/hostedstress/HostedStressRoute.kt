package io.github.fopwoc.mods.testgui.client.gui.ui.page.hostedstress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun HostedStressRoute(viewModel: HostedStressViewModel = viewModel(HostedStressViewModel::class)) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()

  HostedStressView(
      state = state,
      onSelectedIndexChange = viewModel::selectIndex,
      onFocusField = viewModel::focusField,
      onCycleFocus = viewModel::cycleFocus,
      onLoadSelection = viewModel::loadSelectionIntoFocusedField,
      onMirrorSelection = viewModel::mirrorSelectionIntoAllFields,
      onMirrorEnabledChange = viewModel::toggleMirror,
      onCommitSnapshot = viewModel::commitSnapshot,
  )
}
