package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.BlockSelection
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.ClipboardOperation
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementGeometry
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementRecord
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementSelectionState
import io.github.fopwoc.mods.gtnhmeasurement.client.measurement.MeasurementShortcutScheme as Keys
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession
import net.minecraft.client.Minecraft

object MeasurementEditorRuntimeSnapshot {
  fun read(minecraft: Minecraft = Minecraft.getMinecraft()): MeasurementEditorModel {
    val selectedMode = MeasurementSession.mode
    val dimensionId = minecraft.theWorld?.provider?.dimensionId
    val measurements =
        dimensionId?.let(MeasurementSelectionState::measurementsForDimension).orEmpty()
    val entries = measurements.map { record ->
      MeasurementEntry(
          id = record.id,
          label = describe(record),
          selected = MeasurementSelectionState.isSelected(record.id),
      )
    }
    val clipboard = MeasurementSelectionState.activeClipboard

    return MeasurementEditorModel(
        selectedMode = selectedMode,
        contextLabel =
            when (dimensionId) {
              null -> "No world loaded"
              else -> "Dimension $dimensionId · ${measurements.size} measurements"
            },
        entries = entries,
        selectedCount = entries.count(MeasurementEntry::selected),
        canUndo = MeasurementSelectionState.canUndo,
        canRedo = MeasurementSelectionState.canRedo,
        clipboardLabel =
            when (clipboard?.operation) {
              null -> ""
              ClipboardOperation.COPY -> "${clipboard.measurements.size} copied"
              ClipboardOperation.CUT -> "${clipboard.measurements.size} cut"
              ClipboardOperation.MOVE -> "moving ${clipboard.measurements.size}"
              ClipboardOperation.RESIZE -> "resizing"
            },
        shortcuts = shortcuts(),
    )
  }

  fun describe(record: MeasurementRecord): String {
    val size =
        when (record.mode) {
          MeasurementMode.LINE ->
              MeasurementGeometry.formatDistance(
                  MeasurementGeometry.lineDistance(record.first, record.second)
              )
          MeasurementMode.AREA -> MeasurementGeometry.area(record.first, record.second).label
          MeasurementMode.SPHERE -> MeasurementGeometry.sphere(record.first, record.second).label
          MeasurementMode.DISABLED -> ""
        }
    return "${record.mode.displayName} $size · ${record.first.coords()} → ${record.second.coords()}"
  }

  private fun BlockSelection.coords(): String = "$x,$y,$z"

  private fun shortcuts(): List<ShortcutReference> =
      listOf(
          ShortcutReference(Keys.createClickLabel(), "create / place anchor"),
          ShortcutReference(Keys.targetedCreateClickLabel(), "target adjacent block face"),
          ShortcutReference(Keys.selectionClickLabel(), "select measurement"),
          ShortcutReference(Keys.multiSelectionClickLabel(), "add to selection"),
          ShortcutReference(Keys.transformClickLabel(), "move or resize"),
          ShortcutReference(Keys.constraintModifierLabel(), "hold to constrain to right angles"),
          ShortcutReference(Keys.editClipboardKeys(), "copy / cut / paste"),
          ShortcutReference("${Keys.undoLabel()} / ${Keys.redoLabel()}", "undo / redo"),
          ShortcutReference(Keys.deleteLabel(), "delete selection"),
          ShortcutReference(Keys.cancelLabel(), "cancel interaction"),
      )
}
