package io.github.fopwoc.mods.framework.network

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client-only half of [NeoForgeNetworkBackend]. */
internal object NeoForgeClientNetworking {
    fun canSend(type: CustomPacketPayload.Type<FramePayload>): Boolean =
        Minecraft.getInstance().connection?.hasChannel(type) == true

    fun send(type: CustomPacketPayload.Type<FramePayload>, frame: ByteArray) {
        if (!canSend(type)) return
        /*? if >=26 {*/
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            FramePayload(type, frame)
        )
        /*?} else {*/
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(FramePayload(type, frame))
         */
        /*?}*/
    }
}
