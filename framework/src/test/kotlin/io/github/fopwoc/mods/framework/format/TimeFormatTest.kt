package io.github.fopwoc.mods.framework.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatTest {
  @Test
  fun adaptivePicksUnitByMagnitude() {
    assertEquals("123 ms", TimeFormat.millisAdaptive(123.4))
    assertEquals("4.13 ms", TimeFormat.millisAdaptive(4.125))
    assertEquals("410 µs", TimeFormat.millisAdaptive(0.41))
    assertEquals("5 µs", TimeFormat.millisAdaptive(0.005))
    assertEquals("0 µs", TimeFormat.millisAdaptive(0.0))
  }

  @Test
  fun fixedKeepsTwoDecimals() {
    assertEquals("0.41 ms", TimeFormat.millis(0.41))
    assertEquals("12.00 ms", TimeFormat.millis(12.0))
  }
}
