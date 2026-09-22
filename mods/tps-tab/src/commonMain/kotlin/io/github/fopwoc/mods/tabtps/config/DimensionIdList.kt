package io.github.fopwoc.mods.tabtps.config

import io.github.fopwoc.mods.tabtps.protocol.MAX_REQUESTED_DIMENSIONS
import io.github.fopwoc.mods.tabtps.protocol.MAX_DIMENSION_ID_LENGTH

internal object DimensionIdList {
    fun parse(value: String): List<String> =
        value
            .splitToSequence(',')
            .map(String::trim)
            .mapNotNull { id ->
                if (id.length > MAX_DIMENSION_ID_LENGTH) null
                else id.toIntOrNull()?.toString() ?: id.takeIf(RESOURCE_ID::matches)
            }
            .distinct()
            .take(MAX_REQUESTED_DIMENSIONS)
            .toList()

    fun format(dimensionIds: List<String>): String = dimensionIds.joinToString(", ")

    private val RESOURCE_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
}
