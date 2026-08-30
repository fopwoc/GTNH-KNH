package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

object MeasurementActionMapping {
  fun resolveKeyboardActions(snapshot: MeasurementInputSnapshot): List<MeasurementKeyboardAction> =
      buildList {
        if (snapshot.escapeTriggered) {
          add(MeasurementKeyboardAction.CANCEL_ACTIVE_INTERACTION)
          return@buildList
        }
        when {
          snapshot.redoPrimaryTriggered || snapshot.redoSecondaryTriggered ->
              add(MeasurementKeyboardAction.REDO)
          snapshot.undoTriggered -> add(MeasurementKeyboardAction.UNDO)
        }
        if (snapshot.copyTriggered) {
          add(MeasurementKeyboardAction.COPY_SELECTION)
        }
        if (snapshot.cutTriggered) {
          add(MeasurementKeyboardAction.CUT_SELECTION)
        }
        if (snapshot.pasteTriggered) {
          add(MeasurementKeyboardAction.BEGIN_PASTE_PLACEMENT)
        }
        if (snapshot.deleteTriggered) {
          add(MeasurementKeyboardAction.DELETE_SELECTION_OR_CANCEL_DRAFT)
        }
      }

  fun resolveWorldClickAction(
      snapshot: MeasurementInputSnapshot,
      isPastePlacementActive: Boolean,
      hasActiveDraftCreation: Boolean,
  ): MeasurementWorldClickAction =
      when {
        isPastePlacementActive && !snapshot.transformModifierDown -> {
          MeasurementWorldClickAction.PLACE_CLIPBOARD
        }
        hasActiveDraftCreation && !snapshot.transformModifierDown ->
            MeasurementWorldClickAction.REGISTER_ANCHOR
        snapshot.selectionModifierDown && snapshot.targetModifierDown ->
            MeasurementWorldClickAction.SELECT_MULTI
        snapshot.selectionModifierDown -> MeasurementWorldClickAction.SELECT_SINGLE
        snapshot.transformModifierDown -> MeasurementWorldClickAction.BEGIN_TRANSFORM
        else -> MeasurementWorldClickAction.REGISTER_ANCHOR
      }
}
