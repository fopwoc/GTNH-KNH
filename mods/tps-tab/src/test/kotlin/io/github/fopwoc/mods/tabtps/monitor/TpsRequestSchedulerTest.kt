package io.github.fopwoc.mods.tabtps.monitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TpsRequestSchedulerTest {
  private val scheduler = TpsRequestScheduler()

  @Test
  fun requestsImmediatelyThenOncePerSecondWhileTabIsOpen() {
    val first = request(tick = 5)

    assertEquals(1L, first?.requestId)
    assertNull(request(tick = 24))
    assertEquals(2L, request(tick = 25)?.requestId)
  }

  @Test
  fun sendsNothingWithoutBothOpenTabAndServerChannel() {
    assertNull(request(tick = 1, tabOpen = false))
    assertNull(request(tick = 2, serverChannelAvailable = false))

    assertEquals(1L, request(tick = 3)?.requestId)
  }

  @Test
  fun changedDimensionIdsAreRequestedImmediately() {
    val currentOnly = request(tick = 1, dimensionIds = listOf(0))
    val pinnedDimensions = request(tick = 2, dimensionIds = listOf(0, -1, 7))

    assertEquals(listOf(0), currentOnly!!.dimensionIds)
    assertEquals(listOf(0, -1, 7), pinnedDimensions!!.dimensionIds)
  }

  @Test
  fun supportsEveryTickAndSlowUpdateIntervals() {
    assertEquals(1L, request(tick = 1, updateIntervalTicks = 1)?.requestId)
    assertEquals(2L, request(tick = 2, updateIntervalTicks = 1)?.requestId)

    scheduler.reset()

    assertEquals(1L, request(tick = 1, updateIntervalTicks = 200)?.requestId)
    assertNull(request(tick = 200, updateIntervalTicks = 200))
    assertEquals(2L, request(tick = 201, updateIntervalTicks = 200)?.requestId)
  }

  @Test
  fun changedUpdateIntervalIsAppliedImmediately() {
    request(tick = 1, updateIntervalTicks = 200)

    assertEquals(2L, request(tick = 2, updateIntervalTicks = 1)?.requestId)
  }

  private fun request(
      tick: Long,
      tabOpen: Boolean = true,
      serverChannelAvailable: Boolean = true,
      dimensionIds: List<Int> = listOf(0),
      updateIntervalTicks: Int = 20,
  ) =
      scheduler.nextRequest(
          tick = tick,
          tabOpen = tabOpen,
          serverChannelAvailable = serverChannelAvailable,
          dimensionIds = dimensionIds,
          updateIntervalTicks = updateIntervalTicks,
      )
}
