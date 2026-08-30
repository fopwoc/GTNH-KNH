package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.ui.page.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fopwoc.mods.framework.ui.compose.runtime.collectAsStateWithLifecycle

@Composable
fun BackupStatusRoute(
    screenHeight: Int,
    refreshToken: Int,
    viewModel: BackupStatusViewModel = viewModel { BackupStatusViewModel() },
    onClose: () -> Unit,
) {
  LaunchedEffect(refreshToken) {
    viewModel.refresh()
  }

  val model by viewModel.model.collectAsStateWithLifecycle()

  BackupStatusView(
      model = model,
      screenHeight = screenHeight,
      onCaptureNow = viewModel::captureNow,
      onToggleHighlights = viewModel::toggleHighlights,
      onClose = onClose,
  )
}
