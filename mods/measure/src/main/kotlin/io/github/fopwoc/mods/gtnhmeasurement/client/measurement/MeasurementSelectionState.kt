package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlinx.serialization.Serializable
import java.util.ArrayDeque

@Serializable
data class BlockSelection(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimensionId: Int
) {
    fun centerX(): Double = x + 0.5

    fun centerY(): Double = y + 0.5

    fun centerZ(): Double = z + 0.5

    fun isInDimension(targetDimensionId: Int): Boolean = dimensionId == targetDimensionId

    fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): BlockSelection = copy(
        x = x + deltaX,
        y = y + deltaY,
        z = z + deltaZ
    )
}

@Serializable
data class PersistedMeasurement(
    val mode: MeasurementMode,
    val first: BlockSelection,
    val second: BlockSelection
) {
    fun containsAnchor(anchor: BlockSelection): Boolean = first == anchor || second == anchor

    fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): PersistedMeasurement = copy(
        first = first.offset(deltaX, deltaY, deltaZ),
        second = second.offset(deltaX, deltaY, deltaZ)
    )
}

data class MeasurementRecord(
    val id: Long,
    val mode: MeasurementMode,
    val first: BlockSelection,
    val second: BlockSelection
) {
    fun containsAnchor(anchor: BlockSelection): Boolean = first == anchor || second == anchor

    fun anchorRole(anchor: BlockSelection): MeasurementAnchorRole? = when (anchor) {
        first -> MeasurementAnchorRole.FIRST
        second -> MeasurementAnchorRole.SECOND
        else -> null
    }

    fun toPersisted(): PersistedMeasurement = PersistedMeasurement(
        mode = mode,
        first = first,
        second = second
    )
}

enum class MeasurementAnchorRole {
    FIRST,
    SECOND
}

enum class ClipboardOperation {
    COPY,
    CUT,
    MOVE,
    RESIZE
}

data class MeasurementClipboard(
    val operation: ClipboardOperation,
    val originAnchor: BlockSelection,
    val measurements: List<PersistedMeasurement>,
    val sourceMeasurementIds: Set<Long> = emptySet(),
    val resizeAnchorRole: MeasurementAnchorRole? = null
)

object MeasurementSelectionState {
    private const val MAX_HISTORY_SIZE = 100

    private data class SelectionCycleState(
        val anchor: BlockSelection,
        val candidateIds: List<Long>,
        val nextIndex: Int
    )

    private data class EditorSnapshot(
        val measurements: List<MeasurementRecord>,
        val selectedMeasurementIds: List<Long>,
        val clipboard: MeasurementClipboard?,
        val lastAnchorInteraction: BlockSelection?,
        val nextMeasurementId: Long
    )

    private var draftFirstSelection: BlockSelection? = null
    private var draftPreviewSelection: BlockSelection? = null
    private val measurements = mutableListOf<MeasurementRecord>()
    private val selectedMeasurementIds = linkedSetOf<Long>()
    private var clipboard: MeasurementClipboard? = null
    private var pastePlacementActive = false
    private var pastePreviewAnchor: BlockSelection? = null
    private var lastAnchorInteraction: BlockSelection? = null
    private var nextMeasurementId = 1L
    private var persistenceDirty = false
    private var selectionCycleState: SelectionCycleState? = null
    private var pendingPlacementUndoSnapshot: EditorSnapshot? = null
    private val undoHistory = ArrayDeque<EditorSnapshot>()
    private val redoHistory = ArrayDeque<EditorSnapshot>()

    val draftFirst: BlockSelection?
        get() = draftFirstSelection

    val draftSecond: BlockSelection?
        get() = draftPreviewSelection

    val activeClipboard: MeasurementClipboard?
        get() = clipboard

    val isPastePlacementActive: Boolean
        get() = pastePlacementActive && clipboard != null

    val hasActiveDraftCreation: Boolean
        get() = draftFirstSelection != null && !isPastePlacementActive

    val canUndo: Boolean
        get() = undoHistory.isNotEmpty()

    val canRedo: Boolean
        get() = redoHistory.isNotEmpty()

    fun clearTransientState() {
        draftFirstSelection = null
        draftPreviewSelection = null
        pastePlacementActive = false
        pastePreviewAnchor = null
        selectionCycleState = null
        pendingPlacementUndoSnapshot = null
        if (clipboard?.operation == ClipboardOperation.MOVE || clipboard?.operation == ClipboardOperation.RESIZE) {
            clipboard = null
        }
    }

    fun cancelDraftCreation(): Boolean {
        if (!hasActiveDraftCreation) {
            return false
        }

        draftFirstSelection = null
        draftPreviewSelection = null
        selectionCycleState = null
        return true
    }

    fun resetAll() {
        clearTransientState()
        selectedMeasurementIds.clear()
        clipboard = null
        lastAnchorInteraction = null
        measurements.clear()
        nextMeasurementId = 1L
        persistenceDirty = false
        undoHistory.clear()
        redoHistory.clear()
    }

    fun measurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> = measurements.filter {
        it.first.isInDimension(currentDimensionId) && it.second.isInDimension(currentDimensionId)
    }

    fun selectedMeasurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> {
        val selectedIds = selectedMeasurementIds.toSet()
        return measurementsForDimension(currentDimensionId).filter { it.id in selectedIds }
    }

    fun isSelected(measurementId: Long): Boolean = measurementId in selectedMeasurementIds

    fun exportPersistedMeasurements(): List<PersistedMeasurement> = measurements.map(MeasurementRecord::toPersisted)

    fun replacePersistedMeasurements(newMeasurements: List<PersistedMeasurement>) {
        resetAll()
        newMeasurements.forEach { measurement ->
            if (!measurement.mode.isEnabled || measurement.first.dimensionId != measurement.second.dimensionId) {
                return@forEach
            }
            addMeasurementInternal(measurement, markDirty = false)
        }
        persistenceDirty = false
    }

    fun consumePersistenceDirtyFlag(): Boolean {
        val current = persistenceDirty
        persistenceDirty = false
        return current
    }

    fun markPersistenceDirty() {
        persistenceDirty = true
    }

    fun registerMeasurementAnchor(clicked: BlockSelection, mode: MeasurementMode): Boolean {
        if (!mode.isEnabled) {
            return false
        }

        if (draftFirstSelection == null) {
            lastAnchorInteraction = clicked
            draftFirstSelection = clicked
            draftPreviewSelection = null
            return true
        }

        val beforeSnapshot = createSnapshot()
        lastAnchorInteraction = clicked
        val first = draftFirstSelection ?: return false
        val added = addMeasurementInternal(
            PersistedMeasurement(
                mode = mode,
                first = first,
                second = clicked
            )
        )
        draftFirstSelection = null
        draftPreviewSelection = null
        if (added) {
            commitSnapshot(beforeSnapshot)
        }
        return added
    }

    fun updateDraftPreview(block: BlockSelection?) {
        draftPreviewSelection = if (draftFirstSelection != null) block else null
    }

    fun syncForDimension(currentDimensionId: Int) {
        if (draftFirstSelection?.isInDimension(currentDimensionId) == false) {
            draftFirstSelection = null
        }
        if (draftPreviewSelection?.isInDimension(currentDimensionId) == false) {
            draftPreviewSelection = null
        }
        if (pastePreviewAnchor?.isInDimension(currentDimensionId) == false) {
            pastePreviewAnchor = null
        }
        if (clipboard?.originAnchor?.isInDimension(currentDimensionId) == false) {
            clipboard = null
            pastePlacementActive = false
            pendingPlacementUndoSnapshot = null
        }
        if (lastAnchorInteraction?.isInDimension(currentDimensionId) == false) {
            lastAnchorInteraction = null
        }
        val visibleIds = measurementsForDimension(currentDimensionId).mapTo(HashSet(), MeasurementRecord::id)
        selectedMeasurementIds.retainAll(visibleIds)
        selectionCycleState = selectionCycleState?.takeIf { cycle ->
            cycle.anchor.isInDimension(currentDimensionId) && cycle.candidateIds.any { it in visibleIds }
        }
    }

    fun selectAtAnchor(anchor: BlockSelection, multiSelect: Boolean): Boolean {
        val candidates = measurementsForAnchor(anchor)
        if (candidates.isEmpty()) {
            if (!multiSelect) {
                selectedMeasurementIds.clear()
            }
            selectionCycleState = null
            return false
        }

        lastAnchorInteraction = anchor
        if (multiSelect) {
            selectedMeasurementIds.addAll(candidates.map(MeasurementRecord::id))
            selectionCycleState = null
            return true
        }

        val selected = nextCycledMeasurement(anchor, candidates)
        selectedMeasurementIds.clear()
        selectedMeasurementIds.add(selected.id)
        return true
    }

    fun copySelected(): Boolean {
        val selectedMeasurements = selectedMeasurementsForClipboard()
        if (selectedMeasurements.isEmpty()) {
            return false
        }

        pendingPlacementUndoSnapshot = null
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.COPY,
            originAnchor = resolveClipboardOrigin(selectedMeasurements),
            measurements = selectedMeasurements.map(MeasurementRecord::toPersisted)
        )
        pastePlacementActive = false
        pastePreviewAnchor = null
        return true
    }

    fun cutSelected(): Boolean {
        val selectedMeasurements = selectedMeasurementsForClipboard()
        if (selectedMeasurements.isEmpty()) {
            return false
        }

        val beforeSnapshot = createSnapshot()
        pendingPlacementUndoSnapshot = null
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.CUT,
            originAnchor = resolveClipboardOrigin(selectedMeasurements),
            measurements = selectedMeasurements.map(MeasurementRecord::toPersisted)
        )
        removeMeasurementsById(selectedMeasurements.mapTo(HashSet(), MeasurementRecord::id))
        selectedMeasurementIds.clear()
        pastePlacementActive = false
        pastePreviewAnchor = null
        draftFirstSelection = null
        draftPreviewSelection = null
        return commitSnapshot(beforeSnapshot)
    }

    fun deleteSelected(): Int {
        val selectedIds = selectedMeasurementIds.toSet()
        if (selectedIds.isEmpty()) {
            return 0
        }

        val beforeSnapshot = createSnapshot()
        pendingPlacementUndoSnapshot = null
        val removed = removeMeasurementsById(selectedIds)
        selectedMeasurementIds.clear()
        if (removed > 0) {
            commitSnapshot(beforeSnapshot)
        }
        return removed
    }

    fun beginPastePlacement(): Boolean {
        if (clipboard == null) {
            return false
        }

        pastePlacementActive = true
        pastePreviewAnchor = lastAnchorInteraction?.takeIf { anchor ->
            clipboard?.originAnchor?.dimensionId == anchor.dimensionId
        }
        draftFirstSelection = null
        draftPreviewSelection = null
        return true
    }

    fun updatePastePreview(block: BlockSelection?) {
        pastePreviewAnchor = if (isPastePlacementActive) block else null
    }

    fun previewMeasurementsForDimension(currentDimensionId: Int): List<PersistedMeasurement> {
        val anchor = pastePreviewAnchor ?: return emptyList()
        return transformedClipboard(anchor).filter {
            it.first.isInDimension(currentDimensionId) && it.second.isInDimension(currentDimensionId)
        }
    }

    fun placeClipboardAt(anchor: BlockSelection): Boolean {
        if (!isPastePlacementActive) {
            return false
        }

        val activeClipboard = clipboard ?: return false
        val transformed = transformedClipboard(anchor, activeClipboard)
        if (transformed.isEmpty()) {
            return false
        }

        if ((activeClipboard.operation == ClipboardOperation.MOVE || activeClipboard.operation == ClipboardOperation.RESIZE) &&
            transformed == activeClipboard.measurements
        ) {
            pastePlacementActive = false
            pastePreviewAnchor = null
            draftFirstSelection = null
            draftPreviewSelection = null
            clipboard = null
            pendingPlacementUndoSnapshot = null
            return true
        }

        val beforeSnapshot = pendingPlacementUndoSnapshot ?: createSnapshot()
        if (activeClipboard.operation == ClipboardOperation.MOVE || activeClipboard.operation == ClipboardOperation.RESIZE) {
            removeMeasurementsById(activeClipboard.sourceMeasurementIds)
        }

        addMeasurementsInternal(transformed)
        selectedMeasurementIds.clear()
        pastePlacementActive = false
        pastePreviewAnchor = null
        draftFirstSelection = null
        draftPreviewSelection = null
        if (activeClipboard.operation != ClipboardOperation.COPY) {
            clipboard = null
        }
        pendingPlacementUndoSnapshot = null
        commitSnapshot(beforeSnapshot)
        return true
    }

    fun beginMoveAtAnchor(anchor: BlockSelection): Boolean {
        val anchorCandidates = measurementsForAnchor(anchor)
        if (anchorCandidates.isEmpty()) {
            return false
        }

        lastAnchorInteraction = anchor
        val selectedMeasurements = selectedMeasurementsForDimension(anchor.dimensionId)
        val anchorSelectedMeasurements = selectedMeasurements.filter { it.containsAnchor(anchor) }
        val targetMeasurements = if (anchorSelectedMeasurements.isNotEmpty()) {
            selectedMeasurements
        } else {
            listOf(nextCycledMeasurement(anchor, anchorCandidates))
        }

        if (anchorSelectedMeasurements.isEmpty()) {
            return beginResizeAtAnchor(anchor, targetMeasurements.single())
        }

        pendingPlacementUndoSnapshot = createSnapshot()
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.MOVE,
            originAnchor = anchor,
            measurements = targetMeasurements.map(MeasurementRecord::toPersisted),
            sourceMeasurementIds = targetMeasurements.mapTo(linkedSetOf(), MeasurementRecord::id)
        )
        selectedMeasurementIds.clear()
        draftFirstSelection = null
        draftPreviewSelection = null
        pastePlacementActive = true
        pastePreviewAnchor = anchor
        return true
    }

    fun undo(): Boolean {
        if (undoHistory.isEmpty()) {
            return false
        }

        val currentSnapshot = createSnapshot()
        pushHistorySnapshot(redoHistory, currentSnapshot)
        restoreSnapshot(undoHistory.removeLast())
        return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) {
            return false
        }

        val currentSnapshot = createSnapshot()
        pushHistorySnapshot(undoHistory, currentSnapshot)
        restoreSnapshot(redoHistory.removeLast())
        return true
    }

    private fun transformedClipboard(anchor: BlockSelection): List<PersistedMeasurement> {
        val activeClipboard = clipboard ?: return emptyList()
        return transformedClipboard(anchor, activeClipboard)
    }

    private fun transformedClipboard(anchor: BlockSelection, activeClipboard: MeasurementClipboard): List<PersistedMeasurement> {
        if (activeClipboard.originAnchor.dimensionId != anchor.dimensionId) {
            return emptyList()
        }

        if (activeClipboard.operation == ClipboardOperation.RESIZE) {
            val resizeAnchorRole = activeClipboard.resizeAnchorRole ?: return emptyList()
            return activeClipboard.measurements.map { measurement ->
                when (resizeAnchorRole) {
                    MeasurementAnchorRole.FIRST -> measurement.copy(first = anchor)
                    MeasurementAnchorRole.SECOND -> measurement.copy(second = anchor)
                }
            }
        }

        val deltaX = anchor.x - activeClipboard.originAnchor.x
        val deltaY = anchor.y - activeClipboard.originAnchor.y
        val deltaZ = anchor.z - activeClipboard.originAnchor.z
        return activeClipboard.measurements.map { it.offset(deltaX, deltaY, deltaZ) }
    }

    private fun selectedMeasurementsForClipboard(): List<MeasurementRecord> {
        val selectedIds = selectedMeasurementIds.toSet()
        return measurements.filter { it.id in selectedIds }
    }

    private fun resolveClipboardOrigin(selectedMeasurements: List<MeasurementRecord>): BlockSelection {
        val preferredAnchor = lastAnchorInteraction
        if (preferredAnchor != null && selectedMeasurements.any { it.containsAnchor(preferredAnchor) }) {
            return preferredAnchor
        }

        return selectedMeasurements
            .flatMap { listOf(it.first, it.second) }
            .minWithOrNull(blockSelectionComparator)
            ?: selectedMeasurements.first().first
    }

    private fun measurementsForAnchor(anchor: BlockSelection): List<MeasurementRecord> = measurements
        .filter { it.containsAnchor(anchor) }
        .sortedBy(MeasurementRecord::id)

    private fun nextCycledMeasurement(anchor: BlockSelection, candidates: List<MeasurementRecord>): MeasurementRecord {
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

    private fun beginResizeAtAnchor(anchor: BlockSelection, measurement: MeasurementRecord): Boolean {
        val resizeAnchorRole = measurement.anchorRole(anchor) ?: return false
        pendingPlacementUndoSnapshot = createSnapshot()
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.RESIZE,
            originAnchor = anchor,
            measurements = listOf(measurement.toPersisted()),
            sourceMeasurementIds = setOf(measurement.id),
            resizeAnchorRole = resizeAnchorRole
        )
        selectedMeasurementIds.clear()
        draftFirstSelection = null
        draftPreviewSelection = null
        pastePlacementActive = true
        pastePreviewAnchor = anchor
        return true
    }

    private fun createSnapshot(): EditorSnapshot = EditorSnapshot(
        measurements = measurements.toList(),
        selectedMeasurementIds = selectedMeasurementIds.toList(),
        clipboard = clipboard?.takeUnless {
            it.operation == ClipboardOperation.MOVE || it.operation == ClipboardOperation.RESIZE
        },
        lastAnchorInteraction = lastAnchorInteraction,
        nextMeasurementId = nextMeasurementId
    )

    private fun restoreSnapshot(snapshot: EditorSnapshot) {
        draftFirstSelection = null
        draftPreviewSelection = null
        pastePlacementActive = false
        pastePreviewAnchor = null
        selectionCycleState = null
        pendingPlacementUndoSnapshot = null

        measurements.clear()
        measurements.addAll(snapshot.measurements)

        selectedMeasurementIds.clear()
        selectedMeasurementIds.addAll(snapshot.selectedMeasurementIds)

        clipboard = snapshot.clipboard
        lastAnchorInteraction = snapshot.lastAnchorInteraction
        nextMeasurementId = snapshot.nextMeasurementId
        persistenceDirty = true
    }

    private fun commitSnapshot(beforeSnapshot: EditorSnapshot): Boolean {
        val afterSnapshot = createSnapshot()
        if (afterSnapshot == beforeSnapshot) {
            return false
        }

        pushHistorySnapshot(undoHistory, beforeSnapshot)
        redoHistory.clear()
        persistenceDirty = true
        return true
    }

    private fun pushHistorySnapshot(history: ArrayDeque<EditorSnapshot>, snapshot: EditorSnapshot) {
        while (history.size >= MAX_HISTORY_SIZE) {
            history.removeFirst()
        }
        history.addLast(snapshot)
    }

    private fun addMeasurementsInternal(items: List<PersistedMeasurement>): Int {
        var addedCount = 0
        items.forEach { measurement ->
            if (addMeasurementInternal(measurement)) {
                addedCount++
            }
        }
        return addedCount
    }

    private fun addMeasurementInternal(measurement: PersistedMeasurement, markDirty: Boolean = true): Boolean {
        if (!measurement.mode.isEnabled || measurement.first.dimensionId != measurement.second.dimensionId) {
            return false
        }
        val newKey = measurement.key()
        if (measurements.any { it.toPersisted().key() == newKey }) {
            return false
        }

        measurements.add(
            MeasurementRecord(
                id = nextMeasurementId++,
                mode = measurement.mode,
                first = measurement.first,
                second = measurement.second
            )
        )
        if (markDirty) {
            persistenceDirty = true
        }
        return true
    }

    private fun removeMeasurementsById(ids: Set<Long>): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val beforeSize = measurements.size
        measurements.removeAll { it.id in ids }
        val removed = beforeSize - measurements.size
        if (removed > 0) {
            persistenceDirty = true
        }
        return removed
    }
}

private val blockSelectionComparator = compareBy<BlockSelection>(
    BlockSelection::dimensionId,
    BlockSelection::x,
    BlockSelection::y,
    BlockSelection::z
)

private fun PersistedMeasurement.key(): String {
    val orderedAnchors = listOf(first, second).sortedWith(blockSelectionComparator)
    return buildString {
        append(mode.name)
        orderedAnchors.forEach { anchor ->
            append('|')
            append(anchor.dimensionId)
            append(':')
            append(anchor.x)
            append(',')
            append(anchor.y)
            append(',')
            append(anchor.z)
        }
    }
}

