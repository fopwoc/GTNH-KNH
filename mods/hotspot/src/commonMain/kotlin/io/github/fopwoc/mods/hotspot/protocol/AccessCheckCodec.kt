package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageWriter

object AccessCheckCodec : MessageCodec<AccessCheck> {

    override fun encode(writer: MessageWriter, payload: AccessCheck) {
        writer.long(payload.nonce)
    }

    override fun decode(reader: MessageReader): AccessCheck = AccessCheck(reader.long())
}
