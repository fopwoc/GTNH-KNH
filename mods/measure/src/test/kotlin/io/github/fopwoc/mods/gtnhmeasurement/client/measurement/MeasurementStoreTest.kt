package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementStoreTest {
    @Test
    fun addMeasurementDeduplicatesByNormalizedKey() {
        val store = MeasurementStore()
        val first = PersistedMeasurement(MeasurementMode.LINE, anchorA, anchorB)
        val reversed = PersistedMeasurement(MeasurementMode.LINE, anchorB, anchorA)

        assertTrue(store.addMeasurement(first))
        assertFalse(store.addMeasurement(reversed))
        assertEquals(1, store.measurementsForDimension(dimensionId).size)
    }

    @Test
    fun snapshotAndRestorePreserveMeasurementsAndSelection() {
        val store = MeasurementStore()
        assertTrue(store.addMeasurement(PersistedMeasurement(MeasurementMode.AREA, anchorA, anchorB)))
        val measurementId = store.measurementsForDimension(dimensionId).single().id
        store.replaceSelection(listOf(measurementId))

        val snapshot = store.snapshot()
        store.removeMeasurementsById(setOf(measurementId))
        store.clearSelection()
        store.restore(snapshot)

        assertEquals(1, store.measurementsForDimension(dimensionId).size)
        assertTrue(store.isSelected(measurementId))
    }

    @Test
    fun sphereDeduplicatesByCenterAndRadius() {
        val store = MeasurementStore()
        val first = PersistedMeasurement(MeasurementMode.SPHERE, anchorA, sphereEdgeX)
        val sameRadiusDifferentDirection = PersistedMeasurement(MeasurementMode.SPHERE, anchorA, sphereEdgeZ)

        assertTrue(store.addMeasurement(first))
        assertFalse(store.addMeasurement(sameRadiusDifferentDirection))
        assertEquals(1, store.measurementsForDimension(dimensionId).size)
    }

    private companion object {
        const val dimensionId = 0
        val anchorA = BlockSelection(0, 64, 0, dimensionId)
        val anchorB = BlockSelection(2, 64, 0, dimensionId)
        val sphereEdgeX = BlockSelection(2, 64, 0, dimensionId)
        val sphereEdgeZ = BlockSelection(0, 64, 2, dimensionId)
    }
}

