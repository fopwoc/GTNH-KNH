package io.github.fopwoc.mods.framework.network

import io.github.fopwoc.mods.framework.platform.toGamePlayer
import io.github.fopwoc.mods.framework.player.GamePlayer
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer

/** Fabric networking drops payloads a peer never registered, so either side may lack the mod. */
class FabricNetworkBackend : NetworkBackend {
    private val types = ConcurrentHashMap<ModChannel, CustomPacketPayload.Type<FramePayload>>()
    private val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

    @Volatile private var server: MinecraftServer? = null

    init {
        ServerLifecycleEvents.SERVER_STARTING.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register { server = null }
    }

    override fun register(channel: ModChannel) {
        val type = FramePayload.type(channel)
        val codec = FramePayload.codec(type)
        PayloadTypeRegistry.serverboundPlay().register(type, codec)
        PayloadTypeRegistry.clientboundPlay().register(type, codec)
        ServerPlayNetworking.registerGlobalReceiver(type) { payload, context ->
            channel.receive(payload.frame, context.player().toGamePlayer())
        }
        if (isClient) FabricClientNetworking.register(type, channel)
        types[channel] = type
    }

    override fun sendToServer(channel: ModChannel, frame: ByteArray) =
        FabricClientNetworking.send(type(channel), frame)

    override fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray) {
        val entity = server?.playerList?.getPlayer(player.id) ?: return
        val type = type(channel)
        if (ServerPlayNetworking.canSend(entity, type))
            ServerPlayNetworking.send(entity, FramePayload(type, frame))
    }

    override fun isAvailableOnServer(channel: ModChannel): Boolean =
        FabricClientNetworking.canSend(type(channel))

    private fun type(channel: ModChannel) =
        checkNotNull(types[channel]) { "Channel ${channel.id} is not registered" }
}
