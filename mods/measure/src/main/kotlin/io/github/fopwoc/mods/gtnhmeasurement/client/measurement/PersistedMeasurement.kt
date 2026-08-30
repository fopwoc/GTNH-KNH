package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import io.github.fopwoc.mods.gtnhmeasurement.measurement.MeasurementMode
import kotlinx.serialization.Serializable

@Serializable
data class PersistedMeasurement(
    val mode: MeasurementMode,
    val first: BlockSelection,
    val second: BlockSelection,
) {
  fun containsAnchor(anchor: BlockSelection): Boolean = first == anchor || second == anchor

  fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): PersistedMeasurement =
      copy(
          first = first.offset(deltaX, deltaY, deltaZ),
          second = second.offset(deltaX, deltaY, deltaZ),
      )
}
