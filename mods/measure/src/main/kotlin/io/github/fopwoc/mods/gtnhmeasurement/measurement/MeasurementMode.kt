package io.github.fopwoc.mods.gtnhmeasurement.measurement

import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementMode(val displayName: String) {
    DISABLED("Disabled"),
    LINE("Line"),
    AREA("Area");

    val isEnabled: Boolean
        get() = this != DISABLED
}


