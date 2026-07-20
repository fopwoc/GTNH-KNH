package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

internal class MeasurementClipboardState {
    var clipboard: MeasurementClipboard? = null
        private set

    var pastePreviewAnchor: BlockSelection? = null
        private set

    var lastAnchorInteraction: BlockSelection? = null
        private set

    private var pastePlacementActive = false

    val isPastePlacementActive: Boolean
        get() = pastePlacementActive && clipboard != null

    fun reset() {
        clipboard = null
        pastePlacementActive = false
        pastePreviewAnchor = null
        lastAnchorInteraction = null
    }

    fun setLastAnchorInteraction(anchor: BlockSelection) {
        lastAnchorInteraction = anchor
    }

    fun clearTransformPlacement() {
        pastePlacementActive = false
        pastePreviewAnchor = null
    }

    fun clearTransientPlacementState() {
        clearTransformPlacement()
    }

    fun clearTransformClipboardIfNeeded() {
        if (clipboard?.operation == ClipboardOperation.MOVE || clipboard?.operation == ClipboardOperation.RESIZE) {
            clipboard = null
        }
    }

    fun clearClipboard() {
        clipboard = null
    }

    fun clearLastAnchorInteractionIfOutOfDimension(currentDimensionId: Int) {
        if (lastAnchorInteraction?.isInDimension(currentDimensionId) == false) {
            lastAnchorInteraction = null
        }
    }

    fun syncForDimension(currentDimensionId: Int): Boolean {
        if (pastePreviewAnchor?.isInDimension(currentDimensionId) == false) {
            pastePreviewAnchor = null
        }
        if (clipboard?.originAnchor?.isInDimension(currentDimensionId) == false) {
            clipboard = null
            clearTransformPlacement()
            return true
        }
        clearLastAnchorInteractionIfOutOfDimension(currentDimensionId)
        return false
    }

    fun copyFrom(originAnchor: BlockSelection, measurements: List<PersistedMeasurement>) {
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.COPY,
            originAnchor = originAnchor,
            measurements = measurements
        )
        clearTransformPlacement()
    }

    fun cutFrom(originAnchor: BlockSelection, measurements: List<PersistedMeasurement>) {
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.CUT,
            originAnchor = originAnchor,
            measurements = measurements
        )
        clearTransformPlacement()
    }

    fun moveFrom(originAnchor: BlockSelection, measurements: List<PersistedMeasurement>, sourceMeasurementIds: Set<Long>) {
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.MOVE,
            originAnchor = originAnchor,
            measurements = measurements,
            sourceMeasurementIds = sourceMeasurementIds
        )
        pastePlacementActive = true
        pastePreviewAnchor = originAnchor
    }

    fun resizeFrom(originAnchor: BlockSelection, measurement: MeasurementRecord, resizeAnchorRole: MeasurementAnchorRole) {
        clipboard = MeasurementClipboard(
            operation = ClipboardOperation.RESIZE,
            originAnchor = originAnchor,
            measurements = listOf(measurement.toPersisted()),
            sourceMeasurementIds = setOf(measurement.id),
            resizeAnchorRole = resizeAnchorRole
        )
        pastePlacementActive = true
        pastePreviewAnchor = originAnchor
    }

    fun beginPastePlacement(): Boolean {
        val activeClipboard = clipboard ?: return false
        pastePlacementActive = true
        pastePreviewAnchor = lastAnchorInteraction?.takeIf { anchor ->
            activeClipboard.originAnchor.dimensionId == anchor.dimensionId
        }
        return true
    }

    fun updatePastePreview(block: BlockSelection?) {
        pastePreviewAnchor = if (isPastePlacementActive) block else null
    }

    fun transformedClipboard(anchor: BlockSelection): List<PersistedMeasurement> {
        val activeClipboard = clipboard ?: return emptyList()
        return transformedClipboard(anchor, activeClipboard)
    }

    fun transformedClipboard(anchor: BlockSelection, activeClipboard: MeasurementClipboard): List<PersistedMeasurement> {
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

    fun snapshotClipboardForHistory(): MeasurementClipboard? = clipboard?.takeUnless {
        it.operation == ClipboardOperation.MOVE || it.operation == ClipboardOperation.RESIZE
    }

    fun restoreFromHistory(snapshotClipboard: MeasurementClipboard?, snapshotLastAnchorInteraction: BlockSelection?) {
        clipboard = snapshotClipboard
        lastAnchorInteraction = snapshotLastAnchorInteraction
        clearTransformPlacement()
    }
}

