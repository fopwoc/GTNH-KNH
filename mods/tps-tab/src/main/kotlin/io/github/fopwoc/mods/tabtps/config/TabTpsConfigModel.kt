package io.github.fopwoc.mods.tabtps.config

import kotlinx.serialization.Serializable

@Serializable
data class TabTpsConfigModel(
    val enabled: Boolean = DEFAULT_ENABLED,
    val showAllDimensions: Boolean = DEFAULT_SHOW_ALL_DIMENSIONS,
    val updateIntervalTicks: Int = DEFAULT_UPDATE_INTERVAL_TICKS,
    val staleDataTicks: Int = DEFAULT_STALE_DATA_TICKS,
    val showPlaceholder: Boolean = DEFAULT_SHOW_PLACEHOLDER,
    val placeholderText: String = DEFAULT_PLACEHOLDER_TEXT,
)
