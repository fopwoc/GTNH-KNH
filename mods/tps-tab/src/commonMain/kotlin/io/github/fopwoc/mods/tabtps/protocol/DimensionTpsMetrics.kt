package io.github.fopwoc.mods.tabtps.protocol

data class DimensionTpsMetrics(
    val dimensionId: String,
    val dimensionName: String,
    val metrics: TpsMetrics,
)
