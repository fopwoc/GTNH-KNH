package io.github.fopwoc.mods.gtnhclientworldbackup.client.gui.ui.page.status

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.ClientWorldBackupManager
import io.github.fopwoc.mods.gtnhclientworldbackup.backup.model.BackupStatusSnapshot
import io.github.fopwoc.mods.gtnhclientworldbackup.client.highlight.BackedUpChunkHighlighter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupStatusViewModel : ViewModel() {
  private val mutableModel = MutableStateFlow(loadModel())

  val model = mutableModel.asStateFlow()

  fun refresh() {
    mutableModel.value = loadModel()
  }

  fun captureNow() {
    ClientWorldBackupManager.captureNowFromUi()
    refresh()
  }

  fun toggleHighlights() {
    BackedUpChunkHighlighter.toggleHighlights()
    refresh()
  }

  private fun loadModel(): BackupStatusModel {
    val snapshot = ClientWorldBackupManager.getStatusSnapshot()
    return snapshot.toModel(ClientWorldBackupManager.canCaptureNow())
  }
}

private fun BackupStatusSnapshot.toModel(captureNowEnabled: Boolean): BackupStatusModel =
    BackupStatusModel(
        statusLine = statusLine,
        detailLine = detailLine,
        saveName = saveName,
        sourceName = sourceName,
        sourceAddress = sourceAddress,
        currentDimensionId = currentDimensionId,
        totalUniqueChunks = totalUniqueChunks,
        currentDimensionChunkCount = currentDimensionChunkCount,
        nextAutosaveSeconds = nextAutosaveSeconds,
        captureNowEnabled = captureNowEnabled,
        highlightsEnabled = highlightsEnabled,
        highlightLegend = highlightLegend,
        notes = notes,
    )
