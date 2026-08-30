package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

object MeasurementSelectionState {
  private const val MAX_HISTORY_SIZE = 100

  private val store = MeasurementStore()
  private val transientState = MeasurementEditorTransientState()
  private val clipboardState = MeasurementClipboardState()
  private val history = MeasurementHistory(MAX_HISTORY_SIZE)

  val draftFirst: BlockSelection?
    get() = transientState.draftFirst

  val draftSecond: BlockSelection?
    get() = transientState.draftSecond

  val activeClipboard: MeasurementClipboard?
    get() = clipboardState.clipboard

  val isPastePlacementActive: Boolean
    get() = clipboardState.isPastePlacementActive

  val hasActiveDraftCreation: Boolean
    get() = transientState.hasActiveDraftCreation(isPastePlacementActive)

  val canUndo: Boolean
    get() = history.canUndo

  val canRedo: Boolean
    get() = history.canRedo

  val hasSelection: Boolean
    get() = store.hasSelection()

  fun clearTransientState() {
    transientState.clearDraftAndCycle()
    clipboardState.clearTransientPlacementState()
    history.clearPendingPlacementUndoSnapshot()
    clipboardState.clearTransformClipboardIfNeeded()
  }

  fun cancelDraftCreation(): Boolean {
    return transientState.cancelDraftCreation(isPastePlacementActive)
  }

  fun cancelActiveInteraction(): Boolean {
    var cancelled = false
    if (cancelDraftCreation()) {
      cancelled = true
    }
    if (isPastePlacementActive) {
      clipboardState.clearTransientPlacementState()
      clipboardState.clearTransformClipboardIfNeeded()
      history.clearPendingPlacementUndoSnapshot()
      cancelled = true
    }
    if (store.hasSelection()) {
      store.clearSelection()
      cancelled = true
    }
    return cancelled
  }

  fun resetAll() {
    clearTransientState()
    store.reset()
    clipboardState.reset()
    history.reset()
  }

  fun measurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> =
      store.measurementsForDimension(currentDimensionId)

  fun selectedMeasurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> =
      store.selectedMeasurementsForDimension(currentDimensionId)

  fun measurementsContainingBlock(block: BlockSelection): List<MeasurementRecord> =
      store.measurementsContainingBlock(block)

  fun isSelected(measurementId: Long): Boolean = store.isSelected(measurementId)

  fun exportPersistedMeasurements(): List<PersistedMeasurement> =
      store.exportPersistedMeasurements()

  fun replacePersistedMeasurements(newMeasurements: List<PersistedMeasurement>) {
    resetAll()
    store.replacePersistedMeasurements(newMeasurements)
  }

  fun consumePersistenceDirtyFlag(): Boolean = store.consumePersistenceDirtyFlag()

  fun markPersistenceDirty() {
    store.markPersistenceDirty()
  }

  fun registerMeasurementAnchor(
      clicked: BlockSelection,
      mode: MeasurementMode,
      constrainToRightAngles: Boolean = false,
  ): Boolean {
    if (!mode.isEnabled) {
      return false
    }

    if (draftFirst == null) {
      clipboardState.setLastAnchorInteraction(clicked)
      transientState.startDraft(clicked)
      return true
    }

    val beforeSnapshot = createSnapshot()
    val first = draftFirst ?: return false
    val resolvedSecond =
        resolveDraftSecondAnchor(
            first = first,
            clicked = clicked,
            mode = mode,
            constrainToRightAngles = constrainToRightAngles,
        )
    clipboardState.setLastAnchorInteraction(resolvedSecond)
    val added =
        store.addMeasurement(
            PersistedMeasurement(
                mode = mode,
                first = first,
                second = resolvedSecond,
            )
        )
    transientState.clearDraftAndCycle()
    if (added) {
      commitSnapshot(beforeSnapshot)
    }
    return added
  }

  fun updateDraftPreview(
      block: BlockSelection?,
      mode: MeasurementMode,
      constrainToRightAngles: Boolean = false,
  ) {
    transientState.updateDraftPreview(
        block?.let { preview ->
          draftFirst?.let { first ->
            resolveDraftSecondAnchor(
                first = first,
                clicked = preview,
                mode = mode,
                constrainToRightAngles = constrainToRightAngles,
            )
          } ?: preview
        }
    )
  }

  fun syncForDimension(currentDimensionId: Int) {
    if (clipboardState.syncForDimension(currentDimensionId)) {
      history.clearPendingPlacementUndoSnapshot()
    }
    val visibleIds =
        measurementsForDimension(currentDimensionId).mapTo(HashSet(), MeasurementRecord::id)
    store.retainSelections(visibleIds)
    transientState.syncForDimension(currentDimensionId, visibleIds)
  }

  fun selectAtAnchor(anchor: BlockSelection, multiSelect: Boolean): Boolean {
    val candidates = store.measurementsContainingBlock(anchor)
    if (candidates.isEmpty()) {
      if (!multiSelect) {
        store.clearSelection()
      }
      transientState.clearSelectionCycle()
      return false
    }

    clipboardState.setLastAnchorInteraction(anchor)
    if (multiSelect) {
      store.addSelectedIds(candidates.map(MeasurementRecord::id))
      transientState.clearSelectionCycle()
      return true
    }

    val selected = transientState.nextCycledMeasurement(anchor, candidates)
    store.replaceSelection(listOf(selected.id))
    return true
  }

  fun copySelected(): Boolean {
    val selectedMeasurements = store.selectedMeasurements()
    if (selectedMeasurements.isEmpty()) {
      return false
    }

    history.clearPendingPlacementUndoSnapshot()
    clipboardState.copyFrom(
        originAnchor = resolveClipboardOrigin(selectedMeasurements),
        measurements = selectedMeasurements.map(MeasurementRecord::toPersisted),
    )
    return true
  }

  fun cutSelected(): Boolean {
    val selectedMeasurements = store.selectedMeasurements()
    if (selectedMeasurements.isEmpty()) {
      return false
    }

    val beforeSnapshot = createSnapshot()
    history.clearPendingPlacementUndoSnapshot()
    clipboardState.cutFrom(
        originAnchor = resolveClipboardOrigin(selectedMeasurements),
        measurements = selectedMeasurements.map(MeasurementRecord::toPersisted),
    )
    store.removeMeasurementsById(selectedMeasurements.mapTo(HashSet(), MeasurementRecord::id))
    store.clearSelection()
    transientState.clearDraftAndCycle()
    return commitSnapshot(beforeSnapshot)
  }

  fun deleteSelected(): Int {
    val selectedIds = store.selectedMeasurements().mapTo(HashSet(), MeasurementRecord::id)
    if (selectedIds.isEmpty()) {
      return 0
    }

    val beforeSnapshot = createSnapshot()
    history.clearPendingPlacementUndoSnapshot()
    val removed = store.removeMeasurementsById(selectedIds)
    store.clearSelection()
    if (removed > 0) {
      commitSnapshot(beforeSnapshot)
    }
    return removed
  }

  fun beginPastePlacement(): Boolean {
    if (!clipboardState.beginPastePlacement()) {
      return false
    }

    transientState.clearDraftAndCycle()
    return true
  }

  fun updatePastePreview(block: BlockSelection?, constrainToRightAngles: Boolean = false) {
    clipboardState.updatePastePreview(
        block?.let { preview -> resolvePlacementAnchor(preview, constrainToRightAngles) }
    )
  }

  fun previewMeasurementsForDimension(currentDimensionId: Int): List<PersistedMeasurement> {
    val anchor = clipboardState.pastePreviewAnchor ?: return emptyList()
    return clipboardState.transformedClipboard(anchor).filter {
      it.first.isInDimension(currentDimensionId) && it.second.isInDimension(currentDimensionId)
    }
  }

  fun placeClipboardAt(anchor: BlockSelection, constrainToRightAngles: Boolean = false): Boolean {
    if (!isPastePlacementActive) {
      return false
    }

    val activeClipboard = activeClipboard ?: return false
    val resolvedAnchor = resolvePlacementAnchor(anchor, constrainToRightAngles)
    val transformed = clipboardState.transformedClipboard(resolvedAnchor, activeClipboard)
    if (transformed.isEmpty()) {
      return false
    }

    if (
        (activeClipboard.operation == ClipboardOperation.MOVE ||
            activeClipboard.operation == ClipboardOperation.RESIZE) &&
            transformed == activeClipboard.measurements
    ) {
      clipboardState.clearTransformPlacement()
      transientState.clearDraftAndCycle()
      clipboardState.clearClipboard()
      history.clearPendingPlacementUndoSnapshot()
      return true
    }

    val beforeSnapshot = history.pendingSnapshotOrNull ?: createSnapshot()
    if (
        activeClipboard.operation == ClipboardOperation.MOVE ||
            activeClipboard.operation == ClipboardOperation.RESIZE
    ) {
      store.removeMeasurementsById(activeClipboard.sourceMeasurementIds)
    }

    store.addMeasurements(transformed)
    store.clearSelection()
    clipboardState.clearTransformPlacement()
    transientState.clearDraftAndCycle()
    if (activeClipboard.operation != ClipboardOperation.COPY) {
      clipboardState.clearClipboard()
    }
    history.clearPendingPlacementUndoSnapshot()
    commitSnapshot(beforeSnapshot)
    return true
  }

  fun beginMoveAtAnchor(anchor: BlockSelection): Boolean {
    val anchorCandidates = store.measurementsContainingBlock(anchor)
    if (anchorCandidates.isEmpty()) {
      return false
    }

    clipboardState.setLastAnchorInteraction(anchor)
    val selectedMeasurements = selectedMeasurementsForDimension(anchor.dimensionId)
    val anchorSelectedMeasurements = selectedMeasurements.filter { it.containsBlock(anchor) }
    val targetMeasurements =
        if (anchorSelectedMeasurements.isNotEmpty()) {
          selectedMeasurements
        } else {
          listOf(transientState.nextCycledMeasurement(anchor, anchorCandidates))
        }

    if (anchorSelectedMeasurements.isEmpty()) {
      return beginResizeAtAnchor(anchor, targetMeasurements.single())
    }

    history.rememberPendingPlacementUndoSnapshot(createSnapshot())
    clipboardState.moveFrom(
        originAnchor = anchor,
        measurements = targetMeasurements.map(MeasurementRecord::toPersisted),
        sourceMeasurementIds = targetMeasurements.mapTo(linkedSetOf(), MeasurementRecord::id),
    )
    store.clearSelection()
    transientState.clearDraftAndCycle()
    return true
  }

  fun undo(): Boolean {
    return history.undo(createSnapshot(), ::restoreSnapshot)
  }

  fun redo(): Boolean {
    return history.redo(createSnapshot(), ::restoreSnapshot)
  }

  private fun resolveClipboardOrigin(
      selectedMeasurements: List<MeasurementRecord>
  ): BlockSelection {
    val preferredAnchor = clipboardState.lastAnchorInteraction
    if (
        preferredAnchor != null && selectedMeasurements.any { it.containsAnchor(preferredAnchor) }
    ) {
      return preferredAnchor
    }

    return selectedMeasurements
        .flatMap { listOf(it.first, it.second) }
        .minWithOrNull(blockSelectionComparator) ?: selectedMeasurements.first().first
  }

  private fun beginResizeAtAnchor(anchor: BlockSelection, measurement: MeasurementRecord): Boolean {
    val resizeAnchorRole = measurement.anchorRole(anchor) ?: return false
    history.rememberPendingPlacementUndoSnapshot(createSnapshot())
    clipboardState.resizeFrom(anchor, measurement, resizeAnchorRole)
    store.clearSelection()
    transientState.clearDraftAndCycle()
    return true
  }

  private fun createSnapshot(): MeasurementEditorSnapshot =
      MeasurementEditorSnapshot(
          store = store.snapshot(),
          clipboard = clipboardState.snapshotClipboardForHistory(),
          lastAnchorInteraction = clipboardState.lastAnchorInteraction,
      )

  private fun restoreSnapshot(snapshot: MeasurementEditorSnapshot) {
    transientState.clearDraftAndCycle()
    history.clearPendingPlacementUndoSnapshot()
    store.restore(snapshot.store)
    clipboardState.restoreFromHistory(snapshot.clipboard, snapshot.lastAnchorInteraction)
    store.markPersistenceDirty()
  }

  private fun commitSnapshot(beforeSnapshot: MeasurementEditorSnapshot): Boolean {
    val afterSnapshot = createSnapshot()
    return history.commit(beforeSnapshot, afterSnapshot) {
      store.markPersistenceDirty()
    }
  }

  private fun resolveDraftSecondAnchor(
      first: BlockSelection,
      clicked: BlockSelection,
      mode: MeasurementMode,
      constrainToRightAngles: Boolean,
  ): BlockSelection {
    if (!constrainToRightAngles || mode != MeasurementMode.LINE) {
      return clicked
    }
    return MeasurementGeometry.snapToRightAngle(first, clicked)
  }

  private fun resolvePlacementAnchor(
      anchor: BlockSelection,
      constrainToRightAngles: Boolean,
  ): BlockSelection {
    if (!constrainToRightAngles) {
      return anchor
    }

    val activeClipboard = activeClipboard ?: return anchor
    return when (activeClipboard.operation) {
      ClipboardOperation.RESIZE -> resolveResizePlacementAnchor(anchor, activeClipboard)
      ClipboardOperation.COPY,
      ClipboardOperation.CUT,
      ClipboardOperation.MOVE ->
          MeasurementGeometry.snapToRightAngle(activeClipboard.originAnchor, anchor)
    }
  }

  private fun resolveResizePlacementAnchor(
      anchor: BlockSelection,
      clipboard: MeasurementClipboard,
  ): BlockSelection {
    val measurement = clipboard.measurements.singleOrNull() ?: return anchor
    if (measurement.mode != MeasurementMode.LINE) {
      return anchor
    }

    val fixedAnchor =
        when (clipboard.resizeAnchorRole) {
          MeasurementAnchorRole.FIRST -> measurement.second
          MeasurementAnchorRole.SECOND -> measurement.first
          null -> return anchor
        }
    return MeasurementGeometry.snapToRightAngle(fixedAnchor, anchor)
  }
}

private val blockSelectionComparator =
    compareBy<BlockSelection>(
        BlockSelection::dimensionId,
        BlockSelection::x,
        BlockSelection::y,
        BlockSelection::z,
    )

internal fun PersistedMeasurement.key(): String {
  if (mode == MeasurementMode.SPHERE) {
    return buildString {
      append(mode.name)
      append('|')
      append(first.dimensionId)
      append(':')
      append(first.x)
      append(',')
      append(first.y)
      append(',')
      append(first.z)
      append('|')
      append(MeasurementGeometry.sphereRadiusSquared(first, second))
    }
  }

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
