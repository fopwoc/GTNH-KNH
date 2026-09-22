package io.github.fopwoc.mods.tabtps.network

import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

/** Client side of the TPS channel: holds the last unread snapshot for the monitor to poll. */
object ClientTpsNetwork {
    private var pendingSnapshot: TpsSnapshot? = null

    /** False on servers without TPS Tab, which then never receive a request. */
    val serverChannelAvailable: Boolean
        get() = TpsChannel.isAvailableOnServer

    fun initialize() {
        TpsChannel.snapshots.handle { snapshot -> pendingSnapshot = snapshot }
    }

    fun request(request: TpsRequest) = TpsChannel.requests.send(request)

    fun pollSnapshot(): TpsSnapshot? = pendingSnapshot.also { pendingSnapshot = null }

    fun clearPending() {
        pendingSnapshot = null
    }
}
