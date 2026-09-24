package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlinx.serialization.Serializable

@Serializable
data class PersistedMeasurementSet(
    val version: Int = 1,
    val measurements: List<PersistedMeasurement> = emptyList(),
)
