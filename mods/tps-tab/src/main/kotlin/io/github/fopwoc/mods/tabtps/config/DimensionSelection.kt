package io.github.fopwoc.mods.tabtps.config

internal object DimensionSelection {
  fun requested(
      currentDimensionId: Int,
      includeCurrentDimension: Boolean,
      pinnedDimensionIds: List<Int>,
  ): List<Int> =
      buildList {
            if (includeCurrentDimension) {
              add(currentDimensionId)
            }
            addAll(pinnedDimensionIds)
          }
          .distinct()
}
