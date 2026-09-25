package io.github.fopwoc.mods.framework.network

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Client-only half of [FabricNetworkBackend]. */
internal object FabricClientNetworking {
    fun register(type: CustomPacketPayload.Type<FramePayload>, channel: ModChannel) {
        ClientPlayNetworking.registerGlobalReceiver(type) { payload, _ ->
            channel.receive(payload.frame, null)
        }
    }

    fun canSend(type: CustomPacketPayload.Type<FramePayload>): Boolean =
        ClientPlayNetworking.canSend(type)

    fun send(type: CustomPacketPayload.Type<FramePayload>, frame: ByteArray) {
        if (canSend(type)) ClientPlayNetworking.send(FramePayload(type, frame))
    }
}
