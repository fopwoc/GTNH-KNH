package io.github.fopwoc.mods.tabtps.tps

data class ParsedOpisReport(
    val overall: TpsMeasurement? = null,
    val currentDimension: TpsMeasurement? = null,
    val overallLine: String? = null,
    val dimensionLine: String? = null,
)
