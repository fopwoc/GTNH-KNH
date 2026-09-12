package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.netty.buffer.ByteBuf

/** Client → server: start (or join) a profiling run. */
class ProfileRequestMessage() : VersionedMessage<ProfileRequest>(HOTSPOT_PROTOCOL_VERSION) {
  constructor(request: ProfileRequest) : this() {
    payload = request
  }

  override fun encode(buffer: ByteBuf, payload: ProfileRequest) {
    buffer.writeLong(payload.requestId)
    buffer.writeInt(payload.durationTicks.coerceIn(1, MAX_DURATION_TICKS))
  }

  override fun decode(reader: MessageReader): ProfileRequest {
    val requestId = reader.long()
    val durationTicks = reader.int()
    reader.check(durationTicks in 1..MAX_DURATION_TICKS) { "Duration $durationTicks out of range" }
    return ProfileRequest(requestId, durationTicks)
  }
}
