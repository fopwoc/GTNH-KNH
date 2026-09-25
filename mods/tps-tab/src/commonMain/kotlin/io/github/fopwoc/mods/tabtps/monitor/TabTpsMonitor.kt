package io.github.fopwoc.mods.tabtps.monitor

import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.tabtps.config.TabTpsConfig
import io.github.fopwoc.mods.tabtps.network.ClientTpsNetwork

/** Feeds game state into [TpsMonitorState] once per client tick and exposes it to the overlay. */
object TabTpsMonitor {

    data class Snapshot(
        val tickNow: Long,
        val tabOpen: Boolean,
        val measurement: TimedTpsSnapshot?,
        val statusMessage: String?,
    )

    private val state = TpsMonitorState()

    fun snapshot(): Snapshot =
        Snapshot(
            tickNow = state.tickCounter,
            tabOpen = state.tabOpen,
            measurement = state.latestMeasurement,
            statusMessage = statusMessage(),
        )

    fun install() {
        ClientEvents.connected.subscribe { reset() }
        ClientEvents.disconnected.subscribe { reset() }
        ClientEvents.tickEnd.subscribe { tick() }
    }

    private fun reset() {
        state.reset()
        ClientTpsNetwork.clearPending()
    }

    private fun tick() {
        val client = ClientBackend.current
        val dimensionId = client.currentDimensionId
        val connected = client.isInWorld && dimensionId != null
        val request =
            state.tick(
                TpsMonitorInput(
                    connected = connected,
                    tabPressed = client.isPlayerListOpen,
                    overlayEnabled = TabTpsConfig.enabled && TabTpsConfig.hasVisibleMetrics,
                    serverChannelAvailable = ClientTpsNetwork.serverChannelAvailable,
                    requestedDimensionIds =
                        dimensionId?.let(TabTpsConfig::requestedDimensionIds).orEmpty(),
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
