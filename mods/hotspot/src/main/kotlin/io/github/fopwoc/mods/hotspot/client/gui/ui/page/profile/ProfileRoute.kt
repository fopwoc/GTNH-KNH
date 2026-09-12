package io.github.fopwoc.mods.hotspot.client.gui.ui.page.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun ProfileRoute(
    screenWidth: Int,
    screenHeight: Int,
    refreshToken: Int,
    viewModel: ProfileViewModel = viewModel(ProfileViewModel::class),
    onClose: () -> Unit,
) {
  LaunchedEffect(refreshToken) { viewModel.refreshFromRuntime() }

  val state by viewModel.stateFlow.collectAsStateWithLifecycle()

  ProfileView(
      state = state,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      onProfile = viewModel::profile,
      onDurationChange = viewModel::setDuration,
      onPreviousDimension = { viewModel.cycleDimension(-1) },
      onNextDimension = { viewModel.cycleDimension(1) },
      onFocusChunk = viewModel::focusChunk,
      onSelectTileEntities = viewModel::selectTileEntities,
      onClear = viewModel::clear,
      onClose = onClose,
  )
}
