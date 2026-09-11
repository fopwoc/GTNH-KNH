package io.github.fopwoc.mods.tabtps.monitor

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork
import net.minecraft.client.Minecraft

/** Feeds game state into [TpsMonitorState] once per client tick and exposes it to the overlay. */
@SideOnly(Side.CLIENT)
object TabTpsMonitor {
  private const val CONFIG_POLL_INTERVAL_TICKS = 20L

  data class Snapshot(
      val tickNow: Long,
      val tabOpen: Boolean,
      val measurement: TimedTpsSnapshot?,
      val statusMessage: String?,
  )

  private val state = TpsMonitorState()

  // FML connection events are posted from Netty threads; everything else runs on the client
  // thread, so the events only raise a flag that the next tick consumes.
  @Volatile private var resetRequested = false

  fun snapshot(): Snapshot =
      Snapshot(
          tickNow = state.tickCounter,
          tabOpen = state.tabOpen,
          measurement = state.latestMeasurement,
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
      state.reset()
      ClientTpsNetwork.clearPending()
    }

    if ((state.tickCounter + 1) % CONFIG_POLL_INTERVAL_TICKS == 0L) {
      TabTpsConfig.refreshIfChanged()
    }

    val minecraft = Minecraft.getMinecraft()
    val player = minecraft.thePlayer
    val connected = minecraft.theWorld != null && player != null
    val request =
        state.tick(
            TpsMonitorInput(
                connected = connected,
                tabPressed = minecraft.gameSettings.keyBindPlayerList.getIsKeyPressed(),
                overlayEnabled = TabTpsConfig.enabled && TabTpsConfig.hasVisibleMetrics,
                serverChannelAvailable = ClientTpsNetwork.serverChannelAvailable,
                requestedDimensionIds =
                    player?.let { TabTpsConfig.requestedDimensionIds(it.dimension) }.orEmpty(),
                updateIntervalTicks = TabTpsConfig.updateIntervalTicks,
                receivedSnapshot = ClientTpsNetwork.pollSnapshot(),
            )
        )
    request?.let(ClientTpsNetwork::request)
  }

  private fun statusMessage(): String? =
      when (state.status) {
        TpsMonitorState.Status.HIDDEN,
        TpsMonitorState.Status.MEASURED -> null
        TpsMonitorState.Status.SERVER_MISSING -> "TPS Tab is not installed on this server"
        TpsMonitorState.Status.NO_RESPONSE ->
            "No answer from the server's TPS Tab (version mismatch?)"
        TpsMonitorState.Status.WAITING -> TabTpsConfig.placeholderText
      }
}
