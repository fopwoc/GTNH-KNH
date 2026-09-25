package io.github.fopwoc.mods.framework.network

import io.github.fopwoc.mods.framework.platform.toGamePlayer
import io.github.fopwoc.mods.framework.player.GamePlayer
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.fml.ModList
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadHandler
import net.neoforged.neoforge.server.ServerLifecycleHooks

/**
 * Payloads are registered as optional, so NeoForge lets a peer without the mod connect; sends are
 * skipped when the peer did not negotiate the channel.
 */
class NeoForgeNetworkBackend : NetworkBackend {
    private val types = ConcurrentHashMap<ModChannel, CustomPacketPayload.Type<FramePayload>>()

    override fun register(channel: ModChannel) {
        val type = FramePayload.type(channel)
        types[channel] = type
        val container = ModList.get().getModContainerById(channel.namespace).orElseThrow {
            IllegalStateException("Channel ${channel.id} needs a mod with id ${channel.namespace}")
        }
        container.eventBus?.addListener(RegisterPayloadHandlersEvent::class.java) { event ->
            val handler = IPayloadHandler<FramePayload> { payload, context ->
                val sender = if (context.flow() == PacketFlow.SERVERBOUND) context.player().toGamePlayer() else null
                channel.receive(payload.frame, sender)
            }
            // Without an explicit client handler NeoForge expects one from RegisterClientPayloadHandlersEvent.
            event.registrar(channel.protocolVersion.toString()).optional().playBidirectional(type, FramePayload.codec(type), handler, handler)
        }
    }

    override fun sendToServer(channel: ModChannel, frame: ByteArray) = NeoForgeClientNetworking.send(type(channel), frame)

    override fun sendToPlayer(channel: ModChannel, player: GamePlayer, frame: ByteArray) {
        val entity = ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(player.id) ?: return
        val type = type(channel)
        if (entity.connection.hasChannel(type)) PacketDistributor.sendToPlayer(entity, FramePayload(type, frame))
    }

    override fun isAvailableOnServer(channel: ModChannel): Boolean = NeoForgeClientNetworking.canSend(type(channel))

    private fun type(channel: ModChannel) = checkNotNull(types[channel]) { "Channel ${channel.id} is not registered" }
}
