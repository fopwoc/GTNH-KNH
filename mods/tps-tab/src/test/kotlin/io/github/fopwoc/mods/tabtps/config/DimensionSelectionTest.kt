package io.github.fopwoc.mods.tabtps.config

import kotlin.test.Test
import kotlin.test.assertEquals

class DimensionSelectionTest {
  @Test
  fun currentDimensionIsRequestedOnceWhenItIsAlsoPinned() {
    assertEquals(
        listOf(0, -1),
        DimensionSelection.requested(
            currentDimensionId = 0,
            includeCurrentDimension = true,
            pinnedDimensionIds = listOf(0, -1, 0),
        ),
    )
  }

  @Test
  fun pinnedDimensionsRemainRequestedWhenCurrentRowIsHidden() {
    assertEquals(
        listOf(0, -1),
        DimensionSelection.requested(
            currentDimensionId = 7,
            includeCurrentDimension = false,
            pinnedDimensionIds = listOf(0, -1),
        ),
    )
  }
}
