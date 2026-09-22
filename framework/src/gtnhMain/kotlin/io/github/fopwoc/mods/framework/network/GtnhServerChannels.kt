package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import cpw.mods.fml.relauncher.Side
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The channels the connected server advertised through FML `REGISTER`, so a client stays quiet on
 * servers without the mod. FML fires these events on Netty threads; the set is copy-on-write.
 */
internal object GtnhServerChannels {
    private val available = CopyOnWriteArraySet<String>()
    private var installed = false

    @Synchronized
    fun install() {
        if (!installed) {
            installed = true
            FMLCommonHandler.instance().bus().register(this)
        }
    }

    fun isAvailable(name: String): Boolean = name in available

    @SubscribeEvent
    fun onChannelRegistration(event: FMLNetworkEvent.CustomPacketRegistrationEvent<*>) {
        if (event.side != Side.CLIENT) return
        when (event.operation) {
            "REGISTER" -> available.addAll(event.registrations)
            "UNREGISTER" -> available.removeAll(event.registrations.toSet())
        }
    }

    @SubscribeEvent
    fun onDisconnected(@Suppress("UNUSED_PARAMETER") event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        available.clear()
    }
}
