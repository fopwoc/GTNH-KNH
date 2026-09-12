package io.github.fopwoc.mods.hotspot.client.network

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.hotspot.client.profile.ProfileStore
import io.github.fopwoc.mods.hotspot.protocol.HOTSPOT_CHANNEL_NAME
import io.github.fopwoc.mods.hotspot.protocol.HotspotNetwork
import io.github.fopwoc.mods.hotspot.protocol.ProfileRequest

/**
 * Client side of the channel. Message handlers run on the client thread; only the channel
 * registration and disconnect events arrive on Netty threads, hence the volatile flag.
 */
@SideOnly(Side.CLIENT)
object ClientHotspotNetwork {
  @Volatile
  var serverChannelAvailable: Boolean = false
    private set

  fun initialize() {
    HotspotNetwork.installClientHandlers(
        onStatus = { message ->
          ProfileStore.onStatus(
              message.requestId,
              checkNotNull(message.status),
              message.remainingTicks,
          )
        },
        onPart = ProfileStore::onSnapshotPart,
    )
  }

  fun request(request: ProfileRequest): Boolean {
    if (!serverChannelAvailable) {
      return false
    }
    HotspotNetwork.sendRequest(request)
    return true
  }

  @SubscribeEvent
  fun onChannelRegistration(event: FMLNetworkEvent.CustomPacketRegistrationEvent<*>) {
    if (event.side != Side.CLIENT || HOTSPOT_CHANNEL_NAME !in event.registrations) {
      return
    }
    serverChannelAvailable = event.operation == "REGISTER"
  }

  @SubscribeEvent
  fun onDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    serverChannelAvailable = false
    ProfileStore.onDisconnected()
  }
}
