package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.netty.buffer.ByteBuf

/** Client → server request for a TPS snapshot. */
class TpsRequestMessage() : VersionedMessage<TpsRequest>(TPS_PROTOCOL_VERSION) {
  constructor(requestId: Long, dimensionIds: List<Int>) : this() {
    payload = TpsRequest(requestId, dimensionIds.distinct().take(MAX_REQUESTED_DIMENSIONS))
  }

  override fun encode(buffer: ByteBuf, payload: TpsRequest) {
    buffer.writeLong(payload.requestId)
    buffer.writeByte(payload.dimensionIds.size)
    payload.dimensionIds.forEach(buffer::writeInt)
  }

  override fun decode(reader: MessageReader): TpsRequest {
    val requestId = reader.long()
    val dimensionIds = reader.list(MAX_REQUESTED_DIMENSIONS, { unsignedByte() }) { int() }
    return TpsRequest(requestId, dimensionIds.distinct())
  }
}

data class TpsRequest(
    val requestId: Long,
    val dimensionIds: List<Int>,
)
