package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementInteractionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft

object MeasurementEditorRuntimeSnapshot {
  fun read(minecraft: Minecraft = Minecraft.getMinecraft()): MeasurementEditorModel {
    val selectedMode = MeasurementSession.mode
    val world = minecraft.theWorld
    val currentDimensionId = world?.provider?.dimensionId
    val visibleMeasurements =
        currentDimensionId?.let(MeasurementSelectionState::measurementsForDimension).orEmpty()
    val selectedMeasurements =
        currentDimensionId
            ?.let(MeasurementSelectionState::selectedMeasurementsForDimension)
            .orEmpty()
    val previewMeasurements =
        currentDimensionId
            ?.let(MeasurementSelectionState::previewMeasurementsForDimension)
            .orEmpty()
    val hoveredTarget = MeasurementInteractionState.currentHoveredTarget
    val clipboard = MeasurementSelectionState.activeClipboard

    return MeasurementEditorModel(
        selectedMode = selectedMode,
        summary = "Quick mode switch",
        footerText = "",
        modeBadgeText = "Mode · ${selectedMode.displayName}",
        contextLabel =
            when {
              world == null -> "No world loaded"
              else -> "Dim ${world.provider.dimensionId} · ${visibleMeasurements.size} visible"
            },
        visibleMeasurementCount = visibleMeasurements.size,
        selectedMeasurementCount = selectedMeasurements.size,
        draftStatusText = draftStatusText(),
        clipboardOperation = clipboard?.operation,
        clipboardItemCount = clipboard?.measurements?.size ?: 0,
        isPastePlacementActive = MeasurementSelectionState.isPastePlacementActive,
        previewMeasurementCount = previewMeasurements.size,
        canUndo = MeasurementSelectionState.canUndo,
        canRedo = MeasurementSelectionState.canRedo,
        hoverKind = hoveredTarget?.kind,
        hoverTargetText =
            hoveredTarget?.block?.let { block ->
              "(${block.x}, ${block.y}, ${block.z})"
            } ?: "No hovered block",
    )
  }

  private fun draftStatusText(): String {
    val first = MeasurementSelectionState.draftFirst
    val second = MeasurementSelectionState.draftSecond
    val mode = MeasurementSession.mode
    return when {
      first != null && second != null && mode == MeasurementMode.SPHERE -> {
        "Sphere center ${formatBlock(first)} · radius ${formatBlock(second)}"
      }
      first != null && second != null ->
          "Draft from ${formatBlock(first)} to ${formatBlock(second)}"
      first != null && mode == MeasurementMode.SPHERE -> "Sphere center ${formatBlock(first)}"
      first != null -> "Draft anchor ${formatBlock(first)}"
      else -> "No draft selection"
    }
  }

  private fun formatBlock(
      block: io.github.fopwoc.mods.gtnhmeasurement.client.measurement.BlockSelection
  ): String = "(${block.x}, ${block.y}, ${block.z})"
}
