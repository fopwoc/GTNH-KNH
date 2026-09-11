package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.tabtps.protocol.TpsMetrics
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TpsMonitorStateTest {
  private fun input(
      tab: Boolean = true,
      channel: Boolean = true,
      connected: Boolean = true,
      enabled: Boolean = true,
      snapshot: TpsSnapshot? = null,
  ) =
      TpsMonitorInput(
          connected = connected,
          tabPressed = tab,
          overlayEnabled = enabled,
          serverChannelAvailable = channel,
          requestedDimensionIds = listOf(0),
          updateIntervalTicks = 20,
          receivedSnapshot = snapshot,
      )

  private fun snapshot(requestId: Long) =
      TpsSnapshot(requestId, TpsMetrics(20.0, 10.0), 0, emptyList())

  @Test
  fun openingTabRequestsImmediatelyThenWaitsForTheInterval() {
    val state = TpsMonitorState()

    assertNull(state.tick(input(tab = false)))
    assertEquals(TpsMonitorState.Status.HIDDEN, state.status)

    val first = state.tick(input())
    assertEquals(1L, first?.requestId)
    assertEquals(TpsMonitorState.Status.WAITING, state.status)
    repeat(19) { assertNull(state.tick(input())) }
    assertEquals(2L, state.tick(input())?.requestId)
  }

  @Test
  fun responsesBecomeMeasurementsAndStaleIdsAreIgnored() {
    val state = TpsMonitorState()
    state.tick(input())

    state.tick(input(snapshot = snapshot(requestId = 1)))
    assertEquals(TpsMonitorState.Status.MEASURED, state.status)
    assertEquals(1L, state.latestMeasurement?.snapshot?.requestId)

    state.tick(input(snapshot = snapshot(requestId = 0)))
    assertEquals(1L, state.latestMeasurement?.snapshot?.requestId)
  }

  @Test
  fun closingTabDropsTheMeasurementAndReopeningRequestsAgain() {
    val state = TpsMonitorState()
    state.tick(input())
    state.tick(input(snapshot = snapshot(1)))

    state.tick(input(tab = false))
    assertNull(state.latestMeasurement)
    assertEquals(TpsMonitorState.Status.HIDDEN, state.status)

    assertNotNull(state.tick(input()), "reopen must request without waiting for the interval")
  }

  @Test
  fun missingChannelAndSilentServerHaveDistinctStatuses() {
    val state = TpsMonitorState(noResponseTimeoutTicks = 5)

    assertNull(state.tick(input(channel = false)))
    assertEquals(TpsMonitorState.Status.SERVER_MISSING, state.status)

    val silent = TpsMonitorState(noResponseTimeoutTicks = 5)
    repeat(5) { silent.tick(input()) }
    assertEquals(TpsMonitorState.Status.WAITING, silent.status)
    silent.tick(input())
    assertEquals(TpsMonitorState.Status.NO_RESPONSE, silent.status)
  }

  @Test
  fun resetRestartsRequestIds() {
    val state = TpsMonitorState()
    state.tick(input())
    state.reset()

    assertEquals(1L, state.tick(input())?.requestId)
  }
}
