package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ClipboardOperation
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementHoverTargetKind
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

data class MeasurementEditorModel(
    val selectedMode: MeasurementMode = MeasurementMode.DISABLED,
    val availableModes: List<MeasurementMode> =
        listOf(
            MeasurementMode.LINE,
            MeasurementMode.AREA,
            MeasurementMode.SPHERE,
            MeasurementMode.DISABLED,
        ),
    val summary: String =
        "Choose a measurement mode and inspect live editor status while the world interaction layer stays active.",
    val footerText: String = "Select a mode to enable measuring",
    val modeBadgeText: String = "Mode · Disabled",
    val contextLabel: String = "No world loaded",
    val visibleMeasurementCount: Int = 0,
    val selectedMeasurementCount: Int = 0,
    val draftStatusText: String = "No draft selection",
    val clipboardOperation: ClipboardOperation? = null,
    val clipboardItemCount: Int = 0,
    val isPastePlacementActive: Boolean = false,
    val previewMeasurementCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hoverKind: MeasurementHoverTargetKind? = null,
    val hoverTargetText: String = "No hovered block",
) {
  val clipboardStatusText: String = buildString {
    if (clipboardOperation == null) {
      append("Clipboard idle")
      return@buildString
    }
    append(clipboardOperation.name.lowercase().replaceFirstChar(Char::uppercaseChar))
    append(" · ")
    append(clipboardItemCount)
    append(if (clipboardItemCount == 1) " item" else " items")
    if (isPastePlacementActive) {
      append(" · placing")
    }
    if (previewMeasurementCount > 0) {
      append(" · preview ")
      append(previewMeasurementCount)
    }
  }

  val historyStatusText: String =
      "Undo ${if (canUndo) "ready" else "empty"} · Redo ${if (canRedo) "ready" else "empty"}"

  val hoverStatusText: String =
      when (hoverKind) {
        MeasurementHoverTargetKind.DIRECT -> "Hover · direct · $hoverTargetText"
        MeasurementHoverTargetKind.OFFSET -> "Hover · offset · $hoverTargetText"
        null -> hoverTargetText
      }
}
