package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

object AccessReplyCodec : MessageCodec<AccessReply> {

    override fun encode(writer: MessageWriter, payload: AccessReply) {
        writer.long(payload.nonce)
        writer.boolean(payload.allowed)
        writer.boolean(payload.profilerAvailable)
        writer.int(payload.maxDurationTicks)
    }

    override fun decode(reader: MessageReader): AccessReply =
        AccessReply(
            nonce = reader.long(),
            allowed = reader.boolean(),
            profilerAvailable = reader.boolean(),
            maxDurationTicks = reader.int(),
        )
}
