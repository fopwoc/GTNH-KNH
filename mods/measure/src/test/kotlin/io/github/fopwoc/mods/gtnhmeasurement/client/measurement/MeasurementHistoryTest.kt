package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeasurementHistoryTest {
    @Test
    fun commitPushesUndoAndClearsRedo() {
        val history = MeasurementHistory(maxHistorySize = 4)
        val before = snapshot(id = 1L)
        val after = snapshot(id = 2L)

        var committed = false
        assertTrue(history.commit(before, after) { committed = true })
        assertTrue(committed)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun undoAndRedoRestoreSnapshotsInOrder() {
        val history = MeasurementHistory(maxHistorySize = 4)
        val before = snapshot(id = 1L)
        val after = snapshot(id = 2L)
        history.commit(before, after) {}

        var restoredOnUndo: MeasurementEditorSnapshot? = null
        assertTrue(history.undo(currentSnapshot = after) { restoredOnUndo = it })
        assertEquals(before, restoredOnUndo)
        assertTrue(history.canRedo)

        var restoredOnRedo: MeasurementEditorSnapshot? = null
        assertTrue(history.redo(currentSnapshot = before) { restoredOnRedo = it })
        assertEquals(after, restoredOnRedo)
    }

    @Test
    fun pendingSnapshotCanBeRememberedAndCleared() {
        val history = MeasurementHistory(maxHistorySize = 2)
        val pending = snapshot(id = 3L)

        history.rememberPendingPlacementUndoSnapshot(pending)
        assertNotNull(history.pendingSnapshotOrNull)
        history.clearPendingPlacementUndoSnapshot()
        assertEquals(null, history.pendingSnapshotOrNull)
    }

    private fun snapshot(id: Long): MeasurementEditorSnapshot = MeasurementEditorSnapshot(
        store = MeasurementStoreSnapshot(
            measurements = listOf(
                MeasurementRecord(
                    id = id,
                    mode = MeasurementMode.LINE,
                    first = BlockSelection(0, 64, 0, 0),
                    second = BlockSelection(1, 64, 0, 0)
                )
            ),
            selectedMeasurementIds = listOf(id),
            nextMeasurementId = id + 1
        ),
        clipboard = null,
        lastAnchorInteraction = null
    )
}

