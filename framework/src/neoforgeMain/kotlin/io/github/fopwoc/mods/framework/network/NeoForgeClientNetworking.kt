package io.github.fopwoc.mods.framework.network

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.client.network.ClientPacketDistributor

/** Client-only half of [NeoForgeNetworkBackend]. */
internal object NeoForgeClientNetworking {
    fun canSend(type: CustomPacketPayload.Type<FramePayload>): Boolean =
        Minecraft.getInstance().connection?.hasChannel(type) == true

    fun send(type: CustomPacketPayload.Type<FramePayload>, frame: ByteArray) {
        if (canSend(type)) ClientPacketDistributor.sendToServer(FramePayload(type, frame))
    }
}
