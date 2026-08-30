package io.github.fopwoc.mods.tabtps.network

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.tabtps.protocol.TPS_CHANNEL_NAME
import io.github.fopwoc.mods.tabtps.protocol.TPS_PROTOCOL_VERSION
import io.github.fopwoc.mods.tabtps.protocol.TpsNetwork
import io.github.fopwoc.mods.tabtps.protocol.TpsRequestMessage
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot
import java.util.concurrent.atomic.AtomicReference

@SideOnly(Side.CLIENT)
object ClientTpsNetwork {
  private val pendingSnapshot = AtomicReference<TpsSnapshot?>()

  @Volatile
  var serverChannelAvailable: Boolean = false
    private set

  fun initialize() {
    TpsNetwork.installClientHandler { message ->
      if (message.protocolVersion == TPS_PROTOCOL_VERSION) {
        pendingSnapshot.set(message.snapshot)
      }
    }
  }

  fun request(request: TpsRequestMessage) {
    if (serverChannelAvailable) {
      TpsNetwork.requestSnapshot(request)
    }
  }

  fun pollSnapshot(): TpsSnapshot? = pendingSnapshot.getAndSet(null)

  fun clearPending() {
    pendingSnapshot.set(null)
  }

  @SubscribeEvent
  fun onChannelRegistration(event: FMLNetworkEvent.CustomPacketRegistrationEvent<*>) {
    if (event.side != Side.CLIENT || TPS_CHANNEL_NAME !in event.registrations) {
      return
    }

    serverChannelAvailable = event.operation == "REGISTER"
    if (!serverChannelAvailable) {
      clearPending()
    }
  }

  @SubscribeEvent
  fun onDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    serverChannelAvailable = false
    clearPending()
  }
}
