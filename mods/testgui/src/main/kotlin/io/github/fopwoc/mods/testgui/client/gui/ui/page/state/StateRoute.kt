package io.github.fopwoc.mods.testgui.client.gui.ui.page.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.navigation.NavEntryScope
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle
import io.github.fopwoc.mods.testgui.client.gui.ui.TestGuiDestination

@Composable
fun StateRoute(
    scope: NavEntryScope<TestGuiDestination>,
    viewModel: StateViewModel = viewModel(StateViewModel::class),
) {
  val state by viewModel.stateFlow.collectAsStateWithLifecycle()
  var rememberedCounter by remember { mutableStateOf(0) }
  var saveableCounter by rememberSaveable { mutableStateOf(0) }

  StateView(
      state = state,
      rememberedCounter = rememberedCounter,
      saveableCounter = saveableCounter,
      onIncrement = viewModel::increment,
      onDecrement = viewModel::decrement,
      onModeSelected = viewModel::setMode,
      onIncrementRemembered = {
        rememberedCounter += 1
      },
      onIncrementSaveable = {
        saveableCounter += 1
      },
      onOpenCoverDestination = {
        viewModel.recordCoverNavigation()
        scope.push(TestGuiDestination.Controls)
      },
  )
}
