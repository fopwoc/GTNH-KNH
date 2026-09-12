package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Knows which optional mod channels the current server advertises, so a client can stay quiet on
 * servers without the mod instead of sending into the void.
 *
 * FML's registration and disconnect events arrive on Netty threads; the set is copy-on-write and
 * listeners must be thread-safe (or just flip a volatile flag, as [ClientChannelTracker.watch] does
 * internally).
 */
@SideOnly(Side.CLIENT)
object ClientChannelTracker {
  private val available = CopyOnWriteArrayList<String>()
  private val disconnectListeners = CopyOnWriteArrayList<() -> Unit>()
  @Volatile private var disconnectPending = false
  private var registered = false

  /**
   * Returns a live view of one channel's availability; safe to read from any thread. [onDisconnect]
   * runs on the client thread, on the first tick after the connection dropped.
   */
  fun watch(channel: ModChannel, onDisconnect: (() -> Unit)? = null): ChannelAvailability {
    ensureRegistered()
    onDisconnect?.let(disconnectListeners::add)
    return ChannelAvailability(channel.name)
  }

  fun isAvailable(channelName: String): Boolean = channelName in available

  class ChannelAvailability internal constructor(private val channelName: String) {
    val isAvailable: Boolean
      get() = isAvailable(channelName)
  }

  @SubscribeEvent
  fun onChannelRegistration(event: FMLNetworkEvent.CustomPacketRegistrationEvent<*>) {
    if (event.side != Side.CLIENT) {
      return
    }
    when (event.operation) {
      "REGISTER" -> event.registrations.forEach { if (it !in available) available += it }
      "UNREGISTER" -> available.removeAll(event.registrations)
    }
  }

  @SubscribeEvent
  fun onDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
    available.clear()
    disconnectPending = true
  }

  @SubscribeEvent
  fun onClientTick(event: TickEvent.ClientTickEvent) {
    if (event.phase != TickEvent.Phase.END || !disconnectPending) {
      return
    }
    disconnectPending = false
    disconnectListeners.forEach { it() }
  }

  private fun ensureRegistered() {
    if (!registered) {
      registered = true
      FMLCommonHandler.instance().bus().register(this)
    }
  }
}
