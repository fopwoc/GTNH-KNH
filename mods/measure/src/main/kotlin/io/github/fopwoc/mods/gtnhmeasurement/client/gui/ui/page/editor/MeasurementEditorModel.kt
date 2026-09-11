package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

/** One saved measurement in the current dimension as the editor lists it. */
data class MeasurementEntry(
    val id: Long,
    val label: String,
    val selected: Boolean,
)

/** A shortcut reference line: key chip plus what it does. */
data class ShortcutReference(
    val keys: String,
    val action: String,
)

/** Everything the editor screen renders. */
data class MeasurementEditorModel(
    val selectedMode: MeasurementMode = MeasurementMode.DISABLED,
    val availableModes: List<MeasurementMode> =
        listOf(
            MeasurementMode.LINE,
            MeasurementMode.AREA,
            MeasurementMode.SPHERE,
            MeasurementMode.DISABLED,
        ),
    val contextLabel: String = "No world loaded",
    val entries: List<MeasurementEntry> = emptyList(),
    val selectedCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val clipboardLabel: String = "Clipboard empty",
    val shortcuts: List<ShortcutReference> = emptyList(),
) {
  val selectedEntryIndices: Set<Int>
    get() = entries.indices.filterTo(HashSet()) { entries[it].selected }
}
