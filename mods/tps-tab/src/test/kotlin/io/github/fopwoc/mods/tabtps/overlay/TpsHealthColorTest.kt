package io.github.fopwoc.mods.tabtps.overlay

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class TpsHealthColorTest {
  @Test
  fun usesGreenForFullSpeedYellowAtBorderlineAndRedWhenBehind() {
    assertEquals(Color(0xFF55FF55), TpsHealthColor.forTps(20.0))
    assertEquals(Color(0xFFFFD54A), TpsHealthColor.forTps(19.0))
    assertEquals(Color(0xFFFF5555), TpsHealthColor.forTps(18.0))
    assertEquals(Color(0xFFFF5555), TpsHealthColor.forTps(10.0))
  }

  @Test
  fun interpolatesBetweenHealthStops() {
    assertEquals(Color(0xFFAAEA50), TpsHealthColor.forTps(19.5))
    assertEquals(Color(0xFFFF9550), TpsHealthColor.forTps(18.5))
  }

  @Test
  fun msptColorFollowsItsOwnBudget() {
    assertEquals(TpsHealthColor.forTps(20.0), TpsHealthColor.forMspt(30.0))
    assertEquals(TpsHealthColor.forTps(10.0), TpsHealthColor.forMspt(100.0))
    assertEquals(TpsHealthColor.forTps(20.0), TpsHealthColor.forMspt(0.0))
  }
}
