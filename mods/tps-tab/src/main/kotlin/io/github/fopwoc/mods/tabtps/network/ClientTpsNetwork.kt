package io.github.fopwoc.mods.tabtps.network

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.network.ClientChannelTracker
import io.github.fopwoc.mods.tabtps.protocol.TpsChannel
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsRequestMessage
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

/** Client side of the TPS channel: holds the last unread snapshot for the monitor to poll. */
@SideOnly(Side.CLIENT)
object ClientTpsNetwork {
  private val channel = ClientChannelTracker.watch(TpsChannel)
  private var pendingSnapshot: TpsSnapshot? = null

  val serverChannelAvailable: Boolean
    get() = channel.isAvailable

  fun initialize() {
    TpsChannel.snapshots.handle { snapshot -> pendingSnapshot = snapshot }
  }

  fun request(request: TpsRequest) {
    if (channel.isAvailable) {
      TpsChannel.requests.send(TpsRequestMessage(request.requestId, request.dimensionIds))
    }
  }

  fun pollSnapshot(): TpsSnapshot? = pendingSnapshot.also { pendingSnapshot = null }

  fun clearPending() {
    pendingSnapshot = null
  }
}
