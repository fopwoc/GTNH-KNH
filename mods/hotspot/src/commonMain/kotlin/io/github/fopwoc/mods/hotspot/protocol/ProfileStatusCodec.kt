package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

object ProfileStatusCodec : MessageCodec<ProfileStatusUpdate> {

    override fun encode(writer: MessageWriter, payload: ProfileStatusUpdate) {
        writer.long(payload.requestId)
        writer.enum(payload.status)
        writer.int(payload.remainingTicks)
        writer.int(payload.maxDurationTicks)
    }

    override fun decode(reader: MessageReader): ProfileStatusUpdate =
        ProfileStatusUpdate(
            requestId = reader.long(),
            status = reader.enum<ProfileStatus>(),
            remainingTicks = reader.int(),
            maxDurationTicks = reader.int(),
        )
}
