package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlin.math.floor

/**
 * Walks a ray through block space. Anchors of existing measurements are picked wherever they sit
 * along the ray — including in mid-air and in front of a solid hit — otherwise the first solid
 * block wins, and failing that the farthest air block within reach.
 */
internal object MeasurementRayPicker {
  const val STEP = 0.1

  data class Pick(val block: BlockSelection, val isAnchor: Boolean)

  fun pick(
      originX: Double,
      originY: Double,
      originZ: Double,
      directionX: Double,
      directionY: Double,
      directionZ: Double,
      maxDistance: Double,
      dimensionId: Int,
      isLoaded: (x: Int, y: Int, z: Int) -> Boolean,
      isSolid: (x: Int, y: Int, z: Int) -> Boolean,
      isAnchor: (BlockSelection) -> Boolean,
  ): Pick? {
    var farthestAir: BlockSelection? = null
    var lastVisited: BlockSelection? = null
    var distance = STEP
    while (distance <= maxDistance + STEP * 0.5) {
      val x = floor(originX + directionX * distance).toInt()
      val y = floor(originY + directionY * distance).toInt()
      val z = floor(originZ + directionZ * distance).toInt()
      val block = BlockSelection(x, y, z, dimensionId)
      if (block != lastVisited) {
        lastVisited = block
        if (isAnchor(block)) {
          return Pick(block, isAnchor = true)
        }
        if (isLoaded(x, y, z)) {
          if (isSolid(x, y, z)) {
            return Pick(block, isAnchor = false)
          }
          farthestAir = block
        }
      }
      distance += STEP
    }
    return farthestAir?.let { Pick(it, isAnchor = false) }
  }
}
