package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.serialization.WorldScopedSync

/**
 * Loads the measurements of the world or server the client joins, saves them a second after they
 * change and on leaving, and keeps the selection on the current dimension.
 */
object MeasurementSaveCycle {
    // Undo/redo and drags mark the store dirty many times per second; batch the JSON writes.
    private val sync =
        WorldScopedSync(
            store = MeasurementPersistence.measurements,
            debounceTicks = 20,
            onLoaded = { loaded ->
                if (loaded == null) MeasurementSelectionState.resetAll()
                else MeasurementSelectionState.replacePersistedMeasurements(loaded.measurements)
            },
            snapshot = {
                PersistedMeasurementSet(
                    measurements = MeasurementSelectionState.exportPersistedMeasurements()
                )
            },
        )

    fun install() {
        ClientEvents.tickEnd.subscribe { tick() }
        ClientEvents.disconnected.subscribe { flush() }
    }

    /** Saves pending changes now; for loaders that announce the world going away, or shutdown. */
    fun flush() {
        if (MeasurementSelectionState.consumePersistenceDirtyFlag()) sync.markDirty()
        sync.flush()
    }

    private fun tick() {
        if (MeasurementSelectionState.consumePersistenceDirtyFlag()) sync.markDirty()
        sync.tick()
        ClientBackend.current.currentDimensionId?.let(MeasurementSelectionState::syncForDimension)
    }
}
