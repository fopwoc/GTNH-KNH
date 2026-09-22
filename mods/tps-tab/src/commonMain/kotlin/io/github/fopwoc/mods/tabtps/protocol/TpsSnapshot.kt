package io.github.fopwoc.mods.tabtps.protocol

data class TpsSnapshot(
    val requestId: Long,
    val server: TpsMetrics,
    val currentDimensionId: String,
    val dimensions: List<DimensionTpsMetrics>,
)
