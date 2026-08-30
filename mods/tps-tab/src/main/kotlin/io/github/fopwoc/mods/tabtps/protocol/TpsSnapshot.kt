package io.github.fopwoc.mods.tabtps.protocol

data class TpsSnapshot(
    val requestId: Long,
    val server: TpsMetrics,
    val currentDimensionId: Int,
    val dimensions: List<DimensionTpsMetrics>,
)
