package io.github.fopwoc.mods.tabtps.tps

data class TpsMeasurement(
    val tps: Double,
    val mspt: Double
)

data class DimensionDescriptor(
    val id: Int,
    val displayName: String,
    val aliases: Set<String>
)

enum class TpsSource {
    PASSIVE_TEXT,
    TIME_SYNC_ESTIMATE
}

data class TimedTpsMeasurement(
    val measurement: TpsMeasurement,
    val sampledAtTick: Long,
    val source: TpsSource,
    val rawLine: String? = null
)

data class ParsedOpisReport(
    val overall: TpsMeasurement? = null,
    val currentDimension: TpsMeasurement? = null,
    val overallLine: String? = null,
    val dimensionLine: String? = null
)


