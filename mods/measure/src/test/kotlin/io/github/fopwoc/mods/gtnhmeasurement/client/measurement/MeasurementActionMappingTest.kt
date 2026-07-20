package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementActionMappingTest {
    @Test
    fun keyboardMappingPrioritizesEscapeCancelAboveEditorActions() {
        val actions = MeasurementActionMapping.resolveKeyboardActions(
            MeasurementInputSnapshot(
                escapeTriggered = true,
                undoTriggered = true,
                copyTriggered = true
            )
        )

        assertEquals(listOf(MeasurementKeyboardAction.CANCEL_ACTIVE_INTERACTION), actions)
    }

    @Test
    fun keyboardMappingPrioritizesRedoOverUndoAndKeepsEditorActionsOrdered() {
        val actions = MeasurementActionMapping.resolveKeyboardActions(
            MeasurementInputSnapshot(
                redoPrimaryTriggered = true,
                undoTriggered = true,
                copyTriggered = true,
                cutTriggered = true,
                pasteTriggered = true,
                deleteTriggered = true
            )
        )

        assertEquals(
            listOf(
                MeasurementKeyboardAction.REDO,
                MeasurementKeyboardAction.COPY_SELECTION,
                MeasurementKeyboardAction.CUT_SELECTION,
                MeasurementKeyboardAction.BEGIN_PASTE_PLACEMENT,
                MeasurementKeyboardAction.DELETE_SELECTION_OR_CANCEL_DRAFT
            ),
            actions
        )
    }

    @Test
    fun keyboardMappingFallsBackToUndoWhenRedoIsNotTriggered() {
        val actions = MeasurementActionMapping.resolveKeyboardActions(
            MeasurementInputSnapshot(undoTriggered = true)
        )

        assertEquals(listOf(MeasurementKeyboardAction.UNDO), actions)
    }

    @Test
    fun worldClickMappingPreservesExistingPriorityOrder() {
        assertEquals(
            MeasurementWorldClickAction.PLACE_CLIPBOARD,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(targetModifierDown = true),
                isPastePlacementActive = true,
                hasActiveDraftCreation = false
            )
        )
        assertEquals(
            MeasurementWorldClickAction.SELECT_MULTI,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(selectionModifierDown = true, targetModifierDown = true),
                isPastePlacementActive = false,
                hasActiveDraftCreation = false
            )
        )
        assertEquals(
            MeasurementWorldClickAction.SELECT_SINGLE,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(selectionModifierDown = true),
                isPastePlacementActive = false,
                hasActiveDraftCreation = false
            )
        )
        assertEquals(
            MeasurementWorldClickAction.BEGIN_TRANSFORM,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(transformModifierDown = true),
                isPastePlacementActive = false,
                hasActiveDraftCreation = false
            )
        )
        assertEquals(
            MeasurementWorldClickAction.REGISTER_ANCHOR,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(),
                isPastePlacementActive = false,
                hasActiveDraftCreation = false
            )
        )
    }

    @Test
    fun worldClickMappingLetsShiftConfirmActiveDraftAndPlacement() {
        assertEquals(
            MeasurementWorldClickAction.REGISTER_ANCHOR,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(selectionModifierDown = true),
                isPastePlacementActive = false,
                hasActiveDraftCreation = true
            )
        )
        assertEquals(
            MeasurementWorldClickAction.PLACE_CLIPBOARD,
            MeasurementActionMapping.resolveWorldClickAction(
                snapshot = MeasurementInputSnapshot(selectionModifierDown = true),
                isPastePlacementActive = true,
                hasActiveDraftCreation = false
            )
        )
    }
}

