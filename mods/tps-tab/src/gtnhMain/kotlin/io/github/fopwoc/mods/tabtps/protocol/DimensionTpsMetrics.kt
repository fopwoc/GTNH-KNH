package io.github.fopwoc.mods.tabtps.protocol

data class DimensionTpsMetrics(
    val dimensionId: Int,
    val dimensionName: String,
    val metrics: TpsMetrics,
)
