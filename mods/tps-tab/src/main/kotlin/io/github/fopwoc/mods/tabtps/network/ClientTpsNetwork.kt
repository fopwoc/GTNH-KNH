package io.github.fopwoc.mods.tabtps.network

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.protocol.TPS_CHANNEL_NAME
import io.github.fopwoc.mods.tabtps.protocol.TpsNetwork
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot

/**
 * Client side of the TPS channel. Message handlers run on the client thread in 1.7.10 (FML queues
 * custom payloads through the vanilla packet queue); only the FML channel registration and
 * disconnect events arrive on Netty threads, which is why [serverChannelAvailable] is volatile.
 */
@SideOnly(Side.CLIENT)
object ClientTpsNetwork {
  private var pendingSnapshot: TpsSnapshot? = null

  @Volatile
  var serverChannelAvailable: Boolean = false
    private set

  fun initialize() {
    TpsNetwork.installClientHandler { snapshot ->
      pendingSnapshot = snapshot
    }
  }

  fun request(request: TpsRequest) {
    if (serverChannelAvailable) {
      TpsNetwork.requestSnapshot(request)
    }
  }

  fun pollSnapshot(): TpsSnapshot? = pendingSnapshot.also { pendingSnapshot = null }

  fun clearPending() {
    pendingSnapshot = null
  }

  @SubscribeEvent
  fun onChannelRegistration(event: FMLNetworkEvent.CustomPacketRegistrationEvent<*>) {
    if (event.side != Side.CLIENT || TPS_CHANNEL_NAME !in event.registrations) {
      return
    }

    serverChannelAvailable = event.operation == "REGISTER"
  }

  @SubscribeEvent
  fun onDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    serverChannelAvailable = false
  }
}
