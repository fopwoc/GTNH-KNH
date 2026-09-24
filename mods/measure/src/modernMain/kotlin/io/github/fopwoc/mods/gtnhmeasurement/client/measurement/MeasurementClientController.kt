package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.event.ClientEvents

/** Owns the client-only save cycle across world joins, disconnects, and dimension changes. */
object MeasurementClientController {
    private var loadedContext: String? = null
    private var dirtyAtTick: Int? = null
    private var tickNumber = 0

    fun install() {
        ClientEvents.tickEnd.subscribe { tick() }
        ClientEvents.disconnected.subscribe { flush() }
    }

    private fun tick() {
        tickNumber++
        val context = MeasurementPersistence.contextId()
        if (context != loadedContext) {
            flush()
            loadedContext = context
            dirtyAtTick = null
            if (context == null) MeasurementSelectionState.resetAll()
            else MeasurementSelectionState.replacePersistedMeasurements(MeasurementPersistence.load(context).measurements)
        }
        if (MeasurementSelectionState.consumePersistenceDirtyFlag() && dirtyAtTick == null) {
            dirtyAtTick = tickNumber
        }
        if (dirtyAtTick?.let { tickNumber - it >= 20 } == true) flush()
        ClientBackend.current.currentDimensionId?.let(MeasurementSelectionState::syncForDimension)
    }

    private fun flush() {
        if (MeasurementSelectionState.consumePersistenceDirtyFlag() && dirtyAtTick == null) dirtyAtTick = tickNumber
        val context = loadedContext ?: return
        if (dirtyAtTick == null) return
        dirtyAtTick = null
        MeasurementPersistence.save(
            context,
            PersistedMeasurementSet(measurements = MeasurementSelectionState.exportPersistedMeasurements()),
        )
    }
}
