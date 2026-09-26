package io.github.fopwoc.mods.framework.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** One [ModChannel] frame as a vanilla custom payload `namespace:path`; the bytes stay opaque. */
class FramePayload(private val type: CustomPacketPayload.Type<FramePayload>, val frame: ByteArray) :
    CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<FramePayload> = type

    companion object {
        fun type(channel: ModChannel): CustomPacketPayload.Type<FramePayload> =
            CustomPacketPayload.Type(id(channel.namespace, channel.path))

        // 26.x renamed Mojang's ResourceLocation to Identifier.
        private fun id(namespace: String, path: String) =
            //? if >=26 {
            net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path)

        //?} else {
        /*net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path)
         */
        //?}

        fun codec(
            type: CustomPacketPayload.Type<FramePayload>
        ): StreamCodec<FriendlyByteBuf, FramePayload> =
            StreamCodec.of(
                { buffer, payload -> buffer.writeBytes(payload.frame) },
                { buffer ->
                    FramePayload(type, ByteArray(buffer.readableBytes()).also(buffer::readBytes))
                },
            )
    }
}
