package io.github.fopwoc.mods.gtnhmeasurement.measurement

object MeasurementSession {
    var mode: MeasurementMode = MeasurementMode.DISABLED
        private set

    val isActive: Boolean
        get() = mode.isEnabled

    fun switchTo(newMode: MeasurementMode) {
        mode = newMode
    }

    fun disable() {
        mode = MeasurementMode.DISABLED
    }
}

