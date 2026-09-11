package io.github.fopwoc.mods.gtnhmeasurement.client.gui.ui.page.editor

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

/** Everything the compact mode dialog renders. */
data class MeasurementEditorModel(
    val selectedMode: MeasurementMode = MeasurementMode.DISABLED,
    val availableModes: List<MeasurementMode> =
        listOf(
            MeasurementMode.LINE,
            MeasurementMode.AREA,
            MeasurementMode.SPHERE,
            MeasurementMode.DISABLED,
        ),
    val summary: String = "Quick mode switch",
    val footerText: String = "",
    val modeBadgeText: String = "Mode · Disabled",
    val contextLabel: String = "No world loaded",
)
