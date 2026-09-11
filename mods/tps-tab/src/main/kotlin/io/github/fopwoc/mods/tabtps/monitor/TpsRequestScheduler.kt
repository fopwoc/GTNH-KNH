package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.tabtps.protocol.TpsRequest

class TpsRequestScheduler {
  private var lastRequestTick: Long? = null
  private var lastDimensionIds: List<Int>? = null
  private var lastUpdateIntervalTicks: Int? = null
  private var nextRequestId = 1L

  fun nextRequest(
      tick: Long,
      tabOpen: Boolean,
      serverChannelAvailable: Boolean,
      dimensionIds: List<Int>,
      updateIntervalTicks: Int,
  ): TpsRequest? {
    if (!tabOpen || !serverChannelAvailable) {
      resetWindow()
      return null
    }

    val previousTick = lastRequestTick
    val normalizedIntervalTicks = updateIntervalTicks.coerceAtLeast(1)
    val normalizedDimensionIds = dimensionIds.distinct()
    val due =
        previousTick == null ||
            tick - previousTick >= normalizedIntervalTicks ||
            lastDimensionIds != normalizedDimensionIds ||
            lastUpdateIntervalTicks != normalizedIntervalTicks
    if (!due) {
      return null
    }

    lastRequestTick = tick
    lastDimensionIds = normalizedDimensionIds
    lastUpdateIntervalTicks = normalizedIntervalTicks
    return TpsRequest(nextRequestId++, normalizedDimensionIds)
  }

  fun reset() {
    resetWindow()
    nextRequestId = 1L
  }

  /** Forgets the request cadence so the next open tab requests immediately, keeping request ids. */
  fun resetWindow() {
    lastRequestTick = null
    lastDimensionIds = null
    lastUpdateIntervalTicks = null
  }
}
