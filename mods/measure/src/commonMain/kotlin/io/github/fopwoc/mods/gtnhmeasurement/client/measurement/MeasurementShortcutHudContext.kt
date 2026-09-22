package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

internal data class MeasurementShortcutHudContext(
    val modeActive: Boolean,
    val selectedMode: MeasurementMode,
    val selectedMeasurementCount: Int,
    val hoveredMeasurementCount: Int,
    val hasDraftCreation: Boolean,
    val draftHasPreview: Boolean,
    val clipboardOperation: ClipboardOperation?,
    val pastePlacementActive: Boolean,
    /** Current freecam targeting reach, or null when the freecam camera is not active. */
    val freecamReach: Int? = null,
)
