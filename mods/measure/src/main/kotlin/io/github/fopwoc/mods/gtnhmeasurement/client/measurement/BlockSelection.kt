package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlinx.serialization.Serializable

@Serializable
data class BlockSelection(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimensionId: Int,
) {
  fun centerX(): Double = x + 0.5

  fun centerY(): Double = y + 0.5

  fun centerZ(): Double = z + 0.5

  fun isInDimension(targetDimensionId: Int): Boolean = dimensionId == targetDimensionId

  fun offset(deltaX: Int, deltaY: Int, deltaZ: Int): BlockSelection =
      copy(
          x = x + deltaX,
          y = y + deltaY,
          z = z + deltaZ,
      )
}
