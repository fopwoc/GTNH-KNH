package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementSession

object MeasurementWorldInteractionController {
    fun syncInteraction() {
        if (!MeasurementSession.isActive || !ClientBackend.current.isInWorld) {
            MeasurementInteractionState.clearHoveredTarget()
            MeasurementSelectionState.updateDraftPreview(null, MeasurementSession.mode)
            MeasurementSelectionState.updatePastePreview(null)
            return
        }
        val dimensionId = ClientBackend.current.currentDimensionId ?: return
        MeasurementSelectionState.syncForDimension(dimensionId)
        val hovered = MeasurementHoverResolver.resolve(usePlacementOffset = MeasurementShortcutScheme.targetModifierDown())
        MeasurementInteractionState.updateHoveredTarget(hovered)
        val input = MeasurementShortcutScheme.currentWorldClickSnapshot()
        MeasurementSelectionState.updateDraftPreview(
            block = if (MeasurementSelectionState.draftFirst != null && !MeasurementSelectionState.isPastePlacementActive) hovered?.block else null,
            mode = MeasurementSession.mode,
            constrainToRightAngles = input.constrainPlacement,
        )
        MeasurementSelectionState.updatePastePreview(
            block = if (MeasurementSelectionState.isPastePlacementActive) hovered?.block else null,
            constrainToRightAngles = input.constrainPlacement,
        )
    }

    /** Returns true when Measure consumed the pick-block click. */
    fun onMiddleClick(): Boolean {
        if (!MeasurementSession.isActive || !ClientBackend.current.isInWorld) return false
        syncInteraction()
        val clicked = MeasurementInteractionState.currentHoveredTarget?.block ?: return false
        val input = MeasurementShortcutScheme.currentWorldClickSnapshot()
        val action = MeasurementActionMapping.resolveWorldClickAction(
            snapshot = input,
            isPastePlacementActive = MeasurementSelectionState.isPastePlacementActive,
            hasActiveDraftCreation = MeasurementSelectionState.hasActiveDraftCreation,
        )
        val handled = when (action) {
            MeasurementWorldClickAction.PLACE_CLIPBOARD -> MeasurementSelectionState.placeClipboardAt(clicked, input.constrainPlacement)
            MeasurementWorldClickAction.SELECT_MULTI -> MeasurementSelectionState.selectAtAnchor(clicked, multiSelect = true)
            MeasurementWorldClickAction.SELECT_SINGLE -> MeasurementSelectionState.selectAtAnchor(clicked, multiSelect = false)
            MeasurementWorldClickAction.BEGIN_TRANSFORM -> MeasurementSelectionState.beginMoveAtAnchor(clicked)
            MeasurementWorldClickAction.REGISTER_ANCHOR -> MeasurementSelectionState.registerMeasurementAnchor(
                clicked, MeasurementSession.mode, input.constrainPlacement,
            )
        }
        if (handled) syncInteraction()
        return handled
    }
}
