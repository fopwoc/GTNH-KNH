package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.tabtps.protocol.TpsRequestMessage

class TpsRequestScheduler {
  private var lastRequestTick: Long? = null
  private var lastIncludeAllDimensions: Boolean? = null
  private var lastUpdateIntervalTicks: Int? = null
  private var nextRequestId = 1L

  fun nextRequest(
      tick: Long,
      tabOpen: Boolean,
      serverChannelAvailable: Boolean,
      includeAllDimensions: Boolean,
      updateIntervalTicks: Int,
  ): TpsRequestMessage? {
    if (!tabOpen || !serverChannelAvailable) {
      resetWindow()
      return null
    }

    val previousTick = lastRequestTick
    val normalizedIntervalTicks = updateIntervalTicks.coerceAtLeast(1)
    val due =
        previousTick == null ||
            tick - previousTick >= normalizedIntervalTicks ||
            lastIncludeAllDimensions != includeAllDimensions ||
            lastUpdateIntervalTicks != normalizedIntervalTicks
    if (!due) {
      return null
    }

    lastRequestTick = tick
    lastIncludeAllDimensions = includeAllDimensions
    lastUpdateIntervalTicks = normalizedIntervalTicks
    return TpsRequestMessage(nextRequestId++, includeAllDimensions)
  }

  fun reset() {
    resetWindow()
    nextRequestId = 1L
  }

  private fun resetWindow() {
    lastRequestTick = null
    lastIncludeAllDimensions = null
    lastUpdateIntervalTicks = null
  }
}
