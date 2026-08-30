package io.github.fopwoc.mods.tabtps.server.sampling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RollingTickWindowTest {
  @Test
  fun averagesMostRecentPopulatedRingSamples() {
    val samples =
        longArrayOf(
            20_000_000,
            40_000_000,
            1_000_000,
            2_000_000,
            10_000_000,
        )

    assertEquals(70.0 / 3.0, RollingTickWindow.averageMilliseconds(samples, 1, 3)!!, 1.0E-9)
  }

  @Test
  fun ignoresUnpopulatedSamplesDuringStartup() {
    val samples = longArrayOf(0, 25_000_000, 0)

    assertEquals(25.0, RollingTickWindow.averageMilliseconds(samples, 1, 3))
    assertNull(RollingTickWindow.averageMilliseconds(LongArray(3), 1, 3))
  }

  @Test
  fun derivesCappedTpsFromMspt() {
    assertEquals(20.0, RollingTickWindow.tpsFor(10.0))
    assertEquals(10.0, RollingTickWindow.tpsFor(100.0))
  }
}
