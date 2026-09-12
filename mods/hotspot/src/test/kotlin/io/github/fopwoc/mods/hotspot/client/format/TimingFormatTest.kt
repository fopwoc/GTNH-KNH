package io.github.fopwoc.mods.hotspot.client.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TimingFormatTest {
  @Test
  fun picksUnitByMagnitude() {
    assertEquals("123 ms", TimingFormat.ms(123.4))
    assertEquals("4.13 ms", TimingFormat.ms(4.125))
    assertEquals("410 µs", TimingFormat.ms(0.41))
    assertEquals("5 µs", TimingFormat.ms(0.005))
    assertEquals("0 µs", TimingFormat.ms(0.0))
  }
}
