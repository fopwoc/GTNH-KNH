package io.github.fopwoc.mods.tabtps.config

import io.github.fopwoc.mods.tabtps.config.DEFAULT_ENABLED
import io.github.fopwoc.mods.tabtps.config.DEFAULT_PLACEHOLDER_TEXT
import io.github.fopwoc.mods.tabtps.config.DEFAULT_SHOW_PLACEHOLDER
import io.github.fopwoc.mods.tabtps.config.DEFAULT_STALE_DATA_TICKS
import kotlinx.serialization.Serializable

@Serializable
data class TabTpsConfigModel(
    val enabled: Boolean = DEFAULT_ENABLED,
    val staleDataTicks: Int = DEFAULT_STALE_DATA_TICKS,
    val showPlaceholder: Boolean = DEFAULT_SHOW_PLACEHOLDER,
    val placeholderText: String = DEFAULT_PLACEHOLDER_TEXT
)
