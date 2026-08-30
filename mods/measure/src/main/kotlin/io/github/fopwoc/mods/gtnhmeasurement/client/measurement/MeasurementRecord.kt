package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode

data class MeasurementRecord(
    val id: Long,
    val mode: MeasurementMode,
    val first: BlockSelection,
    val second: BlockSelection,
) {
  fun containsAnchor(anchor: BlockSelection): Boolean = first == anchor || second == anchor

  fun containsBlock(block: BlockSelection): Boolean {
    if (block.dimensionId != first.dimensionId || block.dimensionId != second.dimensionId) {
      return false
    }
    return when (mode) {
      MeasurementMode.LINE -> containsAnchor(block)
      MeasurementMode.AREA -> {
        block.x in minOf(first.x, second.x)..maxOf(first.x, second.x) &&
            block.y in minOf(first.y, second.y)..maxOf(first.y, second.y) &&
            block.z in minOf(first.z, second.z)..maxOf(first.z, second.z)
      }
      MeasurementMode.SPHERE -> MeasurementGeometry.containsSphereBlock(first, second, block)
      MeasurementMode.DISABLED -> false
    }
  }

  fun anchorRole(anchor: BlockSelection): MeasurementAnchorRole? =
      when (anchor) {
        first -> MeasurementAnchorRole.FIRST
        second -> MeasurementAnchorRole.SECOND
        else -> null
      }

  fun toPersisted(): PersistedMeasurement =
      PersistedMeasurement(
          mode = mode,
          first = first,
          second = second,
      )
}
