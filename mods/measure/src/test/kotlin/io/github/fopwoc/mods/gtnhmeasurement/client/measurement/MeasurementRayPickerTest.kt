package io.github.fopwoc.mods.gtnhmeasurement.client.measurement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementRayPickerTest {
  private fun pick(
      solid: Set<BlockSelection> = emptySet(),
      anchors: Set<BlockSelection> = emptySet(),
      reach: Double = 5.0,
  ): MeasurementRayPicker.Pick? =
      MeasurementRayPicker.pick(
          originX = 0.5,
          originY = 0.5,
          originZ = 0.5,
          directionX = 1.0,
          directionY = 0.0,
          directionZ = 0.0,
          maxDistance = reach,
          dimensionId = 0,
          isLoaded = { _, _, _ -> true },
          isSolid = { x, y, z -> BlockSelection(x, y, z, 0) in solid },
          isAnchor = { it in anchors },
      )

  @Test
  fun anchorInMidAirWinsOverFarthestAirBlock() {
    val anchor = BlockSelection(2, 0, 0, 0)

    val result = pick(anchors = setOf(anchor))

    assertEquals(anchor, result?.block)
    assertTrue(result!!.isAnchor)
  }

  @Test
  fun anchorInFrontOfSolidBlockWinsOverTheHit() {
    val anchor = BlockSelection(2, 0, 0, 0)
    val wall = BlockSelection(4, 0, 0, 0)

    assertEquals(anchor, pick(solid = setOf(wall), anchors = setOf(anchor))?.block)
    assertEquals(wall, pick(solid = setOf(wall))?.block)
  }

  @Test
  fun withoutAnchorsOrSolidsTheFarthestAirBlockIsUsed() {
    assertEquals(BlockSelection(5, 0, 0, 0), pick()?.block)
    assertNull(pick(reach = 0.0))
  }
}
