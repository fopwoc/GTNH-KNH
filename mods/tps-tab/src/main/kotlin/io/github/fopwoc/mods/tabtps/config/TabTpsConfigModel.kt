package io.github.fopwoc.mods.tabtps.config

import kotlinx.serialization.Serializable

@Serializable
data class TabTpsConfigModel(
    val enabled: Boolean = DEFAULT_ENABLED,
    val showServerMetrics: Boolean = DEFAULT_SHOW_SERVER_METRICS,
    val showCurrentDimensionMetrics: Boolean = DEFAULT_SHOW_CURRENT_DIMENSION_METRICS,
    val dimensionIds: String = DEFAULT_DIMENSION_IDS,
    val cardAlignment: String = DEFAULT_CARD_ALIGNMENT,
    val updateIntervalTicks: Int = DEFAULT_UPDATE_INTERVAL_TICKS,
    val staleDataTicks: Int = DEFAULT_STALE_DATA_TICKS,
    val showPlaceholder: Boolean = DEFAULT_SHOW_PLACEHOLDER,
    val placeholderText: String = DEFAULT_PLACEHOLDER_TEXT,
)
