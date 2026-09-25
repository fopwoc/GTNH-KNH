package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

/** Client → server: start (or join) a profiling run. */
object ProfileRequestCodec : MessageCodec<ProfileRequest> {

    override fun encode(writer: MessageWriter, payload: ProfileRequest) {
        writer.long(payload.requestId)
        writer.int(payload.durationTicks.coerceIn(1, MAX_DURATION_TICKS))
    }

    override fun decode(reader: MessageReader): ProfileRequest {
        val requestId = reader.long()
        val durationTicks = reader.int()
        reader.check(durationTicks in 1..MAX_DURATION_TICKS) {
            "Duration $durationTicks out of range"
        }
        return ProfileRequest(requestId, durationTicks)
    }
}
