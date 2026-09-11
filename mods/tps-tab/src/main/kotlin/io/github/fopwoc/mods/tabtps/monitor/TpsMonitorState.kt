package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

/** Everything the monitor reads from the game on one tick. */
data class TpsMonitorInput(
    val connected: Boolean,
    val tabPressed: Boolean,
    val overlayEnabled: Boolean,
    val serverChannelAvailable: Boolean,
    val requestedDimensionIds: List<Int>,
    val updateIntervalTicks: Int,
    val receivedSnapshot: TpsSnapshot?,
)

/** Pure per-tick logic behind the tab overlay: what to show and whether to ask the server. */
class TpsMonitorState(
    private val noResponseTimeoutTicks: Long = 100L,
    private val scheduler: TpsRequestScheduler = TpsRequestScheduler(),
) {
  var tickCounter: Long = 0
    private set

  var tabOpen: Boolean = false
    private set

  var latestMeasurement: TimedTpsSnapshot? = null
    private set

  private var connected = false
  private var tabOpenedAtTick: Long? = null
  private var latestRequestId = 0L
  private var channelAvailable = false

  enum class Status {
    HIDDEN,
    WAITING,
    SERVER_MISSING,
    NO_RESPONSE,
    MEASURED,
  }

  val status: Status
    get() =
        when {
          !connected || !tabOpen -> Status.HIDDEN
          latestMeasurement != null -> Status.MEASURED
          !channelAvailable -> Status.SERVER_MISSING
          tabOpenedAtTick?.let { tickCounter - it >= noResponseTimeoutTicks } == true ->
              Status.NO_RESPONSE
          else -> Status.WAITING
        }

  /** Advances one tick and returns the request to send, if any. */
  fun tick(input: TpsMonitorInput): TpsRequest? {
    tickCounter++
    connected = input.connected
    channelAvailable = input.serverChannelAvailable
    val wasOpen = tabOpen
    tabOpen = input.connected && input.overlayEnabled && input.tabPressed

    if (!tabOpen) {
      if (wasOpen) {
        latestMeasurement = null
      }
      tabOpenedAtTick = null
      scheduler.resetWindow()
      return null
    }

    if (!wasOpen) {
      tabOpenedAtTick = tickCounter
      latestMeasurement = null
      latestRequestId = 0L
    }

    input.receivedSnapshot?.let { response ->
      if (response.requestId >= latestRequestId) {
        latestRequestId = response.requestId
        latestMeasurement = TimedTpsSnapshot(response, tickCounter)
      }
    }

    return scheduler.nextRequest(
        tick = tickCounter,
        tabOpen = true,
        serverChannelAvailable = input.serverChannelAvailable,
        dimensionIds = input.requestedDimensionIds,
        updateIntervalTicks = input.updateIntervalTicks,
    )
  }

  /** New connection: forget everything including request ids. */
  fun reset() {
    connected = false
    tabOpen = false
    tabOpenedAtTick = null
    latestMeasurement = null
    latestRequestId = 0L
    scheduler.reset()
  }
}
