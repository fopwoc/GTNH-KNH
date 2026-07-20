package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

internal data class SelectionCycleState(
    val anchor: BlockSelection,
    val candidateIds: List<Long>,
    val nextIndex: Int
)

internal class MeasurementEditorTransientState {
    private var draftFirstSelection: BlockSelection? = null
    private var draftPreviewSelection: BlockSelection? = null
    private var selectionCycleState: SelectionCycleState? = null

    val draftFirst: BlockSelection?
        get() = draftFirstSelection

    val draftSecond: BlockSelection?
        get() = draftPreviewSelection

    fun hasActiveDraftCreation(isPastePlacementActive: Boolean): Boolean = draftFirstSelection != null && !isPastePlacementActive

    fun clearDraftAndCycle() {
        draftFirstSelection = null
        draftPreviewSelection = null
        selectionCycleState = null
    }

    fun startDraft(anchor: BlockSelection) {
        draftFirstSelection = anchor
        draftPreviewSelection = null
    }

    fun consumeDraftStart(): BlockSelection? {
        val first = draftFirstSelection
        clearDraftAndCycle()
        return first
    }

    fun cancelDraftCreation(isPastePlacementActive: Boolean): Boolean {
        if (!hasActiveDraftCreation(isPastePlacementActive)) {
            return false
        }
        clearDraftAndCycle()
        return true
    }

    fun updateDraftPreview(block: BlockSelection?) {
        draftPreviewSelection = if (draftFirstSelection != null) block else null
    }

    fun clearSelectionCycle() {
        selectionCycleState = null
    }

    fun syncForDimension(currentDimensionId: Int, visibleIds: Set<Long>) {
        if (draftFirstSelection?.isInDimension(currentDimensionId) == false) {
            draftFirstSelection = null
        }
        if (draftPreviewSelection?.isInDimension(currentDimensionId) == false) {
            draftPreviewSelection = null
        }
        selectionCycleState = selectionCycleState?.takeIf { cycle ->
            cycle.anchor.isInDimension(currentDimensionId) && cycle.candidateIds.any { it in visibleIds }
        }
    }

    fun nextCycledMeasurement(anchor: BlockSelection, candidates: List<MeasurementRecord>): MeasurementRecord {
        val candidateIds = candidates.map(MeasurementRecord::id)
        val existingCycle = selectionCycleState
        val index = if (existingCycle != null && existingCycle.anchor == anchor && existingCycle.candidateIds == candidateIds) {
            existingCycle.nextIndex % candidateIds.size
        } else {
            0
        }
        selectionCycleState = SelectionCycleState(
            anchor = anchor,
            candidateIds = candidateIds,
            nextIndex = index + 1
        )
        return candidates[index]
    }
}

