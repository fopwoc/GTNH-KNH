package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

internal data class MeasurementStoreSnapshot(
    val measurements: List<MeasurementRecord>,
    val selectedMeasurementIds: List<Long>,
    val nextMeasurementId: Long
)

internal class MeasurementStore {
    private val measurements = mutableListOf<MeasurementRecord>()
    private val selectedMeasurementIds = linkedSetOf<Long>()
    private var nextMeasurementId = 1L
    private var persistenceDirty = false

    fun reset() {
        measurements.clear()
        selectedMeasurementIds.clear()
        nextMeasurementId = 1L
        persistenceDirty = false
    }

    fun measurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> = measurements.filter {
        it.first.isInDimension(currentDimensionId) && it.second.isInDimension(currentDimensionId)
    }

    fun selectedMeasurementsForDimension(currentDimensionId: Int): List<MeasurementRecord> {
        val selectedIds = selectedMeasurementIds.toSet()
        return measurementsForDimension(currentDimensionId).filter { it.id in selectedIds }
    }

    fun measurementsForAnchor(anchor: BlockSelection): List<MeasurementRecord> = measurements
        .filter { it.containsAnchor(anchor) }
        .sortedBy(MeasurementRecord::id)

    fun measurementsContainingBlock(block: BlockSelection): List<MeasurementRecord> = measurements
        .filter { it.containsBlock(block) }
        .sortedBy(MeasurementRecord::id)

    fun selectedMeasurements(): List<MeasurementRecord> {
        val selectedIds = selectedMeasurementIds.toSet()
        return measurements.filter { it.id in selectedIds }
    }

    fun hasSelection(): Boolean = selectedMeasurementIds.isNotEmpty()

    fun isSelected(measurementId: Long): Boolean = measurementId in selectedMeasurementIds

    fun exportPersistedMeasurements(): List<PersistedMeasurement> = measurements.map(MeasurementRecord::toPersisted)

    fun replacePersistedMeasurements(newMeasurements: List<PersistedMeasurement>) {
        reset()
        newMeasurements.forEach { measurement ->
            addMeasurement(measurement, markDirty = false)
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

    fun clearPersistenceDirty() {
        persistenceDirty = false
    }

    fun clearSelection() {
        selectedMeasurementIds.clear()
    }

    fun addSelectedIds(ids: Collection<Long>) {
        selectedMeasurementIds.addAll(ids)
    }

    fun replaceSelection(ids: Collection<Long>) {
        selectedMeasurementIds.clear()
        selectedMeasurementIds.addAll(ids)
    }

    fun retainSelections(visibleIds: Set<Long>) {
        selectedMeasurementIds.retainAll(visibleIds)
    }

    fun addMeasurements(items: List<PersistedMeasurement>): Int {
        var addedCount = 0
        items.forEach { measurement ->
            if (addMeasurement(measurement)) {
                addedCount++
            }
        }
        return addedCount
    }

    fun addMeasurement(measurement: PersistedMeasurement, markDirty: Boolean = true): Boolean {
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

    fun removeMeasurementsById(ids: Set<Long>): Int {
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

    fun snapshot(): MeasurementStoreSnapshot = MeasurementStoreSnapshot(
        measurements = measurements.toList(),
        selectedMeasurementIds = selectedMeasurementIds.toList(),
        nextMeasurementId = nextMeasurementId
    )

    fun restore(snapshot: MeasurementStoreSnapshot) {
        measurements.clear()
        measurements.addAll(snapshot.measurements)

        selectedMeasurementIds.clear()
        selectedMeasurementIds.addAll(snapshot.selectedMeasurementIds)

        nextMeasurementId = snapshot.nextMeasurementId
    }
}

