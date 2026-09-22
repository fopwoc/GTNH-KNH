package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

/** Client → server request for a TPS snapshot. */
object TpsRequestCodec : MessageCodec<TpsRequest> {
    override fun encode(writer: MessageWriter, payload: TpsRequest) {
        val dimensionIds = payload.dimensionIds.distinct().take(MAX_REQUESTED_DIMENSIONS)
        writer.long(payload.requestId)
        writer.byte(dimensionIds.size)
        dimensionIds.forEach(writer::int)
    }

    override fun decode(reader: MessageReader): TpsRequest {
        val requestId = reader.long()
        val dimensionIds = reader.list(MAX_REQUESTED_DIMENSIONS, { unsignedByte() }) { int() }
        return TpsRequest(requestId, dimensionIds.distinct())
    }
}
