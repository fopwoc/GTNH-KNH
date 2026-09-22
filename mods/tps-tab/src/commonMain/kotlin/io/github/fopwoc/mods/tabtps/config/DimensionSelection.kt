package io.github.fopwoc.mods.tabtps.config

internal object DimensionSelection {
    fun requested(
        currentDimensionId: String,
        includeCurrentDimension: Boolean,
        pinnedDimensionIds: List<String>,
    ): List<String> =
        buildList {
                if (includeCurrentDimension) {
                    add(currentDimensionId)
                }
                addAll(pinnedDimensionIds)
            }
            .distinct()
}
