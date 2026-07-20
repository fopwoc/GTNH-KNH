package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementSelectionStateTest {
    @BeforeTest
    fun setUp() {
        MeasurementSelectionState.resetAll()
        MeasurementInteractionState.clearHoveredTarget()
    }

    @AfterTest
    fun tearDown() {
        MeasurementSelectionState.resetAll()
        MeasurementInteractionState.clearHoveredTarget()
    }

    @Test
    fun undoAndRedoRestoreCreatedMeasurement() {
        createLineMeasurement(anchorA, anchorB)

        assertEquals(1, MeasurementSelectionState.measurementsForDimension(dimensionId).size)
        assertTrue(MeasurementSelectionState.undo())
        assertTrue(MeasurementSelectionState.measurementsForDimension(dimensionId).isEmpty())
        assertTrue(MeasurementSelectionState.redo())

        val restored = MeasurementSelectionState.measurementsForDimension(dimensionId)
        assertEquals(1, restored.size)
        assertEquals(anchorA, restored.single().first)
        assertEquals(anchorB, restored.single().second)
    }

    @Test
    fun copyAndPasteCreateOffsetMeasurement() {
        createLineMeasurement(anchorA, anchorB)

        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertTrue(MeasurementSelectionState.copySelected())
        assertTrue(MeasurementSelectionState.beginPastePlacement())
        assertTrue(MeasurementSelectionState.placeClipboardAt(anchorC))

        val measurements = MeasurementSelectionState.measurementsForDimension(dimensionId)
            .map { it.toPersisted() }
        assertEquals(2, measurements.size)
        assertTrue(measurements.contains(PersistedMeasurement(MeasurementMode.LINE, anchorA, anchorB)))
        assertTrue(measurements.contains(PersistedMeasurement(MeasurementMode.LINE, anchorC, anchorD)))
    }

    @Test
    fun cutAndUndoRestoreMeasurement() {
        createLineMeasurement(anchorA, anchorB)

        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertTrue(MeasurementSelectionState.cutSelected())
        assertTrue(MeasurementSelectionState.measurementsForDimension(dimensionId).isEmpty())
        assertNotNull(MeasurementSelectionState.activeClipboard)

        assertTrue(MeasurementSelectionState.undo())
        val restored = MeasurementSelectionState.measurementsForDimension(dimensionId)
        assertEquals(1, restored.size)
        assertEquals(anchorA, restored.single().first)
        assertEquals(anchorB, restored.single().second)
    }

    @Test
    fun beginMoveAtAnchorMovesSelectedMeasurement() {
        createLineMeasurement(anchorA, anchorB)

        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertTrue(MeasurementSelectionState.beginMoveAtAnchor(anchorA))
        assertEquals(ClipboardOperation.MOVE, MeasurementSelectionState.activeClipboard?.operation)
        assertTrue(MeasurementSelectionState.placeClipboardAt(anchorC))

        val moved = MeasurementSelectionState.measurementsForDimension(dimensionId)
        assertEquals(1, moved.size)
        assertEquals(anchorC, moved.single().first)
        assertEquals(anchorD, moved.single().second)
    }

    @Test
    fun beginMoveAtAnchorWithoutSelectionResizesSingleMeasurement() {
        createLineMeasurement(anchorA, anchorB)

        assertTrue(MeasurementSelectionState.beginMoveAtAnchor(anchorB))
        assertEquals(ClipboardOperation.RESIZE, MeasurementSelectionState.activeClipboard?.operation)
        assertTrue(MeasurementSelectionState.placeClipboardAt(resizedAnchor))

        val resized = MeasurementSelectionState.measurementsForDimension(dimensionId)
        assertEquals(1, resized.size)
        assertEquals(anchorA, resized.single().first)
        assertEquals(resizedAnchor, resized.single().second)
    }

    @Test
    fun areaMeasurementCanBeSelectedFromAnyContainedBlock() {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(areaAnchorA, MeasurementMode.AREA))
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(areaAnchorB, MeasurementMode.AREA))

        assertTrue(MeasurementSelectionState.selectAtAnchor(areaInteriorBlock, multiSelect = false))

        val selected = MeasurementSelectionState.selectedMeasurementsForDimension(dimensionId)
        assertEquals(1, selected.size)
        assertEquals(MeasurementMode.AREA, selected.single().mode)
    }

    @Test
    fun sphereMeasurementCanBeSelectedFromInteriorBlock() {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(sphereCenter, MeasurementMode.SPHERE))
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(sphereRadiusAnchor, MeasurementMode.SPHERE))

        assertTrue(MeasurementSelectionState.selectAtAnchor(sphereInteriorBlock, multiSelect = false))

        val selected = MeasurementSelectionState.selectedMeasurementsForDimension(dimensionId)
        assertEquals(1, selected.size)
        assertEquals(MeasurementMode.SPHERE, selected.single().mode)
    }

    @Test
    fun shiftConstrainedLineDraftSnapsToDominantAxis() {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(anchorA, MeasurementMode.LINE))

        MeasurementSelectionState.updateDraftPreview(diagonalAnchor, MeasurementMode.LINE, constrainToRightAngles = true)
        assertEquals(snappedLineAnchor, MeasurementSelectionState.draftSecond)

        assertTrue(
            MeasurementSelectionState.registerMeasurementAnchor(
                diagonalAnchor,
                MeasurementMode.LINE,
                constrainToRightAngles = true
            )
        )

        val measurement = MeasurementSelectionState.measurementsForDimension(dimensionId).single()
        assertEquals(anchorA, measurement.first)
        assertEquals(snappedLineAnchor, measurement.second)
    }

    @Test
    fun shiftConstrainedMovePlacementSnapsToDominantAxis() {
        createLineMeasurement(anchorA, anchorB)

        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertTrue(MeasurementSelectionState.beginMoveAtAnchor(anchorA))
        assertTrue(MeasurementSelectionState.placeClipboardAt(diagonalMoveAnchor, constrainToRightAngles = true))

        val moved = MeasurementSelectionState.measurementsForDimension(dimensionId).single()
        assertEquals(snappedMoveAnchor, moved.first)
        assertEquals(snappedMoveSecondAnchor, moved.second)
    }

    @Test
    fun cancelActiveInteractionCancelsDraftAndPastePlacement() {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(anchorA, MeasurementMode.LINE))
        assertTrue(MeasurementSelectionState.cancelActiveInteraction())
        assertNull(MeasurementSelectionState.draftFirst)

        createLineMeasurement(anchorA, anchorB)
        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertTrue(MeasurementSelectionState.copySelected())
        assertTrue(MeasurementSelectionState.beginPastePlacement())

        assertTrue(MeasurementSelectionState.cancelActiveInteraction())
        assertFalse(MeasurementSelectionState.isPastePlacementActive)
        assertNotNull(MeasurementSelectionState.activeClipboard)
    }

    @Test
    fun cancelActiveInteractionClearsSelectionWhenOnlySelectionIsActive() {
        createLineMeasurement(anchorA, anchorB)
        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))

        assertTrue(MeasurementSelectionState.hasSelection)
        assertTrue(MeasurementSelectionState.cancelActiveInteraction())
        assertFalse(MeasurementSelectionState.hasSelection)
        assertTrue(MeasurementSelectionState.selectedMeasurementsForDimension(dimensionId).isEmpty())
    }

    @Test
    fun cancelDraftOnlyClearsTransientCreationAndDeleteRemovesSelection() {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(anchorA, MeasurementMode.LINE))
        assertEquals(anchorA, MeasurementSelectionState.draftFirst)
        assertTrue(MeasurementSelectionState.cancelDraftCreation())
        assertNull(MeasurementSelectionState.draftFirst)
        assertFalse(MeasurementSelectionState.cancelDraftCreation())

        createLineMeasurement(anchorA, anchorB)
        assertTrue(MeasurementSelectionState.selectAtAnchor(anchorA, multiSelect = false))
        assertEquals(1, MeasurementSelectionState.deleteSelected())
        assertTrue(MeasurementSelectionState.measurementsForDimension(dimensionId).isEmpty())
    }

    private fun createLineMeasurement(first: BlockSelection, second: BlockSelection) {
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(first, MeasurementMode.LINE))
        assertTrue(MeasurementSelectionState.registerMeasurementAnchor(second, MeasurementMode.LINE))
    }

    private companion object {
        const val dimensionId = 0

        val anchorA = BlockSelection(x = 0, y = 64, z = 0, dimensionId = dimensionId)
        val anchorB = BlockSelection(x = 2, y = 64, z = 0, dimensionId = dimensionId)
        val anchorC = BlockSelection(x = 10, y = 64, z = 0, dimensionId = dimensionId)
        val anchorD = BlockSelection(x = 12, y = 64, z = 0, dimensionId = dimensionId)
        val resizedAnchor = BlockSelection(x = 6, y = 65, z = 1, dimensionId = dimensionId)
        val areaAnchorA = BlockSelection(x = 20, y = 64, z = 20, dimensionId = dimensionId)
        val areaAnchorB = BlockSelection(x = 22, y = 66, z = 22, dimensionId = dimensionId)
        val areaInteriorBlock = BlockSelection(x = 21, y = 65, z = 21, dimensionId = dimensionId)
        val sphereCenter = BlockSelection(x = 30, y = 64, z = 30, dimensionId = dimensionId)
        val sphereRadiusAnchor = BlockSelection(x = 32, y = 64, z = 30, dimensionId = dimensionId)
        val sphereInteriorBlock = BlockSelection(x = 31, y = 64, z = 31, dimensionId = dimensionId)
        val diagonalAnchor = BlockSelection(x = 3, y = 66, z = 1, dimensionId = dimensionId)
        val snappedLineAnchor = BlockSelection(x = 3, y = 64, z = 0, dimensionId = dimensionId)
        val diagonalMoveAnchor = BlockSelection(x = 4, y = 66, z = 2, dimensionId = dimensionId)
        val snappedMoveAnchor = BlockSelection(x = 4, y = 64, z = 0, dimensionId = dimensionId)
        val snappedMoveSecondAnchor = BlockSelection(x = 6, y = 64, z = 0, dimensionId = dimensionId)
    }
}

