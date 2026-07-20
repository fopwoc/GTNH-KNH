package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import java.util.ArrayDeque

internal data class MeasurementEditorSnapshot(
    val store: MeasurementStoreSnapshot,
    val clipboard: MeasurementClipboard?,
    val lastAnchorInteraction: BlockSelection?
)

internal class MeasurementHistory(
    private val maxHistorySize: Int
) {
    private var pendingPlacementUndoSnapshot: MeasurementEditorSnapshot? = null
    private val undoHistory = ArrayDeque<MeasurementEditorSnapshot>()
    private val redoHistory = ArrayDeque<MeasurementEditorSnapshot>()

    val canUndo: Boolean
        get() = undoHistory.isNotEmpty()

    val canRedo: Boolean
        get() = redoHistory.isNotEmpty()

    val pendingSnapshotOrNull: MeasurementEditorSnapshot?
        get() = pendingPlacementUndoSnapshot

    fun rememberPendingPlacementUndoSnapshot(snapshot: MeasurementEditorSnapshot) {
        pendingPlacementUndoSnapshot = snapshot
    }

    fun clearPendingPlacementUndoSnapshot() {
        pendingPlacementUndoSnapshot = null
    }

    fun reset() {
        pendingPlacementUndoSnapshot = null
        undoHistory.clear()
        redoHistory.clear()
    }

    fun undo(currentSnapshot: MeasurementEditorSnapshot, restore: (MeasurementEditorSnapshot) -> Unit): Boolean {
        if (undoHistory.isEmpty()) {
            return false
        }

        pushHistorySnapshot(redoHistory, currentSnapshot)
        restore(undoHistory.removeLast())
        return true
    }

    fun redo(currentSnapshot: MeasurementEditorSnapshot, restore: (MeasurementEditorSnapshot) -> Unit): Boolean {
        if (redoHistory.isEmpty()) {
            return false
        }

        pushHistorySnapshot(undoHistory, currentSnapshot)
        restore(redoHistory.removeLast())
        return true
    }

    fun commit(beforeSnapshot: MeasurementEditorSnapshot, afterSnapshot: MeasurementEditorSnapshot, onCommitted: () -> Unit): Boolean {
        if (afterSnapshot == beforeSnapshot) {
            return false
        }

        pushHistorySnapshot(undoHistory, beforeSnapshot)
        redoHistory.clear()
        onCommitted()
        return true
    }

    private fun pushHistorySnapshot(history: ArrayDeque<MeasurementEditorSnapshot>, snapshot: MeasurementEditorSnapshot) {
        while (history.size >= maxHistorySize) {
            history.removeFirst()
        }
        history.addLast(snapshot)
    }
}

