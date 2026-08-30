package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

data class MeasurementInputSnapshot(
    val selectionModifierDown: Boolean = false,
    val targetModifierDown: Boolean = false,
    val transformModifierDown: Boolean = false,
    val editorModifierDown: Boolean = false,
    val escapeTriggered: Boolean = false,
    val redoPrimaryTriggered: Boolean = false,
    val redoSecondaryTriggered: Boolean = false,
    val undoTriggered: Boolean = false,
    val copyTriggered: Boolean = false,
    val cutTriggered: Boolean = false,
    val pasteTriggered: Boolean = false,
    val deleteTriggered: Boolean = false,
)
