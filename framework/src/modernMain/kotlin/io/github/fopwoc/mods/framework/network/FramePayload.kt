package io.github.fopwoc.mods.framework.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** One [ModChannel] frame as a vanilla custom payload `namespace:path`; the bytes stay opaque. */
class FramePayload(private val type: CustomPacketPayload.Type<FramePayload>, val frame: ByteArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FramePayload> = type

    companion object {
        fun type(channel: ModChannel): CustomPacketPayload.Type<FramePayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(channel.namespace, channel.path))

        fun codec(type: CustomPacketPayload.Type<FramePayload>): StreamCodec<FriendlyByteBuf, FramePayload> =
            StreamCodec.of(
                { buffer, payload -> buffer.writeBytes(payload.frame) },
                { buffer -> FramePayload(type, ByteArray(buffer.readableBytes()).also(buffer::readBytes)) },
            )
    }
}
