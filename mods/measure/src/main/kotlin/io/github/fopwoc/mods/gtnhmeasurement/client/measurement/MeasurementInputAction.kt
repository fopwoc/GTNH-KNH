package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

enum class MeasurementPlatformProfile {
    MAC,
    STANDARD
}

enum class MeasurementKeyboardAction {
    CANCEL_ACTIVE_INTERACTION,
    REDO,
    UNDO,
    COPY_SELECTION,
    CUT_SELECTION,
    BEGIN_PASTE_PLACEMENT,
    DELETE_SELECTION_OR_CANCEL_DRAFT
}

enum class MeasurementWorldClickAction {
    PLACE_CLIPBOARD,
    SELECT_MULTI,
    SELECT_SINGLE,
    BEGIN_TRANSFORM,
    REGISTER_ANCHOR
}

