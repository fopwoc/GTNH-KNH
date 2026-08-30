package io.github.fopwoc.mods.tabtps.tps

data class TimedTpsMeasurement(
    val measurement: TpsMeasurement,
    val sampledAtTick: Long,
    val source: TpsSource,
    val rawLine: String? = null,
)
