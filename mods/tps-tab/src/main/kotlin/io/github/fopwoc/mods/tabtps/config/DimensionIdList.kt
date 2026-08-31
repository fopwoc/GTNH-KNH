package io.github.fopwoc.mods.tabtps.config

import io.github.fopwoc.mods.tabtps.protocol.MAX_REQUESTED_DIMENSIONS

internal object DimensionIdList {
  fun parse(value: String): List<Int> =
      value
          .splitToSequence(',')
          .map(String::trim)
          .mapNotNull(String::toIntOrNull)
          .distinct()
          .take(MAX_REQUESTED_DIMENSIONS)
          .toList()

  fun format(dimensionIds: List<Int>): String = dimensionIds.joinToString(", ")
}
