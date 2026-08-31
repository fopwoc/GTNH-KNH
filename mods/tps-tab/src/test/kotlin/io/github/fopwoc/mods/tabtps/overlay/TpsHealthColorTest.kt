package io.github.fopwoc.mods.tabtps.overlay

import io.github.fopwoc.mods.framework.ui.compose.model.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class TpsHealthColorTest {
  @Test
  fun usesGreenForFullSpeedYellowAtBorderlineAndRedWhenBehind() {
    assertEquals(Color.rgb(0x55, 0xFF, 0x55), TpsHealthColor.forTps(20.0))
    assertEquals(Color.rgb(0xFF, 0xD5, 0x4A), TpsHealthColor.forTps(19.0))
    assertEquals(Color.rgb(0xFF, 0x55, 0x55), TpsHealthColor.forTps(18.0))
    assertEquals(Color.rgb(0xFF, 0x55, 0x55), TpsHealthColor.forTps(10.0))
  }

  @Test
  fun interpolatesBetweenHealthStops() {
    assertEquals(Color.rgb(0xAA, 0xEA, 0x50), TpsHealthColor.forTps(19.5))
    assertEquals(Color.rgb(0xFF, 0x95, 0x50), TpsHealthColor.forTps(18.5))
  }
}
