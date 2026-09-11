package io.github.fopwoc.mods.tabtps.monitor

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import net.minecraft.client.Minecraft

@SideOnly(Side.CLIENT)
object TabTpsMonitor {
  private const val PROTOCOL_TIMEOUT_TICKS = 40L
  private const val CONFIG_POLL_INTERVAL_TICKS = 20L

  data class Snapshot(
      val tickNow: Long,
      val tabOpen: Boolean,
      val measurement: TimedTpsSnapshot?,
      val statusMessage: String?,
  )

  private val requestScheduler = TpsRequestScheduler()

  // FML connection events are posted from Netty threads; everything else runs on the client
  // thread, so the events only raise a flag that the next tick consumes.
  @Volatile private var resetRequested = false

  private var tickCounter = 0L
  private var connected = false
  private var tabOpen = false
  private var tabOpenedAtTick: Long? = null
  private var latestMeasurement: TimedTpsSnapshot? = null
  private var latestRequestId = 0L

  fun snapshot(): Snapshot =
      Snapshot(
          tickNow = tickCounter,
          tabOpen = connected && tabOpen,
          measurement = latestMeasurement,
          statusMessage = statusMessage(),
      )

  @SubscribeEvent
  fun onClientConnected(event: FMLNetworkEvent.ClientConnectedToServerEvent) {
    resetRequested = true
  }

  @SubscribeEvent
  fun onClientDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    resetRequested = true
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }

    if (resetRequested) {
      resetRequested = false
      resetState()
    }

    tickCounter++
    if (tickCounter % CONFIG_POLL_INTERVAL_TICKS == 0L) {
      TabTpsConfig.refreshIfChanged()
    }

    val minecraft = Minecraft.getMinecraft()
    connected = minecraft.theWorld != null && minecraft.thePlayer != null
    val wasOpen = tabOpen
    tabOpen =
        connected &&
            TabTpsConfig.enabled &&
            TabTpsConfig.hasVisibleMetrics &&
            minecraft.gameSettings.keyBindPlayerList.getIsKeyPressed()

    if (!tabOpen) {
      if (wasOpen) {
        latestMeasurement = null
      }
      tabOpenedAtTick = null
      requestScheduler.resetWindow()
      ClientTpsNetwork.clearPending()
      return
    }

    if (!wasOpen) {
      tabOpenedAtTick = tickCounter
      latestMeasurement = null
      latestRequestId = 0L
    }

    ClientTpsNetwork.pollSnapshot()?.let { response ->
      if (response.requestId >= latestRequestId) {
        latestRequestId = response.requestId
        latestMeasurement = TimedTpsSnapshot(response, tickCounter)
      }
    }

    requestScheduler
        .nextRequest(
            tick = tickCounter,
            tabOpen = true,
            serverChannelAvailable = ClientTpsNetwork.serverChannelAvailable,
            dimensionIds =
                TabTpsConfig.requestedDimensionIds(checkNotNull(minecraft.thePlayer).dimension),
            updateIntervalTicks = TabTpsConfig.updateIntervalTicks,
        )
        ?.let(ClientTpsNetwork::request)
  }

  private fun statusMessage(): String? {
    if (!connected || !tabOpen || latestMeasurement != null) {
      return null
    }
    if (!ClientTpsNetwork.serverChannelAvailable) {
      return "TPS Tab is not installed on this server"
    }

    val openedAt = tabOpenedAtTick ?: return TabTpsConfig.placeholderText
    return if (tickCounter - openedAt >= PROTOCOL_TIMEOUT_TICKS) {
      "TPS Tab server protocol does not match"
    } else {
      TabTpsConfig.placeholderText
    }
  }

  private fun resetState() {
    connected = false
    tabOpen = false
    tabOpenedAtTick = null
    latestMeasurement = null
    latestRequestId = 0L
    requestScheduler.reset()
    ClientTpsNetwork.clearPending()
  }
}
