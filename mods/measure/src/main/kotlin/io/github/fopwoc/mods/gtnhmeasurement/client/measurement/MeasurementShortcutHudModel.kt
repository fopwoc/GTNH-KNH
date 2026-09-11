package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color

/** One line of the hint box: the key chip(s) on the left, what they do on the right. */
internal data class MeasurementShortcutHudHint(
    val keys: String,
    val action: String,
    val color: Color = MeasurementShortcutHudPalette.Primary,
) {
  /** Flat form for searching and for hosts without chip rendering. */
  val text: String
    get() = "$keys $action"
}

internal data class MeasurementShortcutHudModel(
    val title: String,
    val hints: List<MeasurementShortcutHudHint>,
)
