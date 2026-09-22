package io.github.fopwoc.mods.framework.network

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.network.FMLNetworkEvent
import io.github.fopwoc.mods.framework.platform.toGamePlayer
import io.netty.buffer.ByteBuf
import net.minecraft.network.NetHandlerPlayServer

/** Hands custom payloads of one channel to it; FML fires these on the receiving main thread. */
internal class GtnhChannelListener(private val channel: ModChannel, private val name: String) {
    @SubscribeEvent
    fun onServerPacket(event: FMLNetworkEvent.ServerCustomPacketEvent) {
        if (event.packet.channel() != name) return
        val sender = (event.handler as NetHandlerPlayServer).playerEntity.toGamePlayer()
        channel.receive(event.packet.payload().bytes(), sender)
    }

    @SubscribeEvent
    fun onClientPacket(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        if (event.packet.channel() != name) return
        channel.receive(event.packet.payload().bytes(), null)
    }

    private fun ByteBuf.bytes(): ByteArray = ByteArray(readableBytes()).also { getBytes(readerIndex(), it) }
}
