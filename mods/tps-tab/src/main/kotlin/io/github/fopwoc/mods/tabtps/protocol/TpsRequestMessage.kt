package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/**
 * Client → server request for a TPS snapshot.
 *
 * Decoding never throws: a foreign protocol version or a truncated payload leaves [request] null so
 * the handler can ignore it. Throwing from `fromBytes` would make FML terminate the connection.
 */
class TpsRequestMessage() : IMessage {
  var request: TpsRequest? = null
    private set

  constructor(requestId: Long, dimensionIds: List<Int>) : this() {
    request = TpsRequest(requestId, dimensionIds.distinct().take(MAX_REQUESTED_DIMENSIONS))
  }

  override fun fromBytes(buffer: ByteBuf) {
    request = null
    if (buffer.readableBytes() < Int.SIZE_BYTES || buffer.readInt() != TPS_PROTOCOL_VERSION) {
      return
    }
    if (buffer.readableBytes() < Long.SIZE_BYTES + 1) {
      return
    }
    val requestId = buffer.readLong()
    val dimensionCount = buffer.readUnsignedByte().toInt()
    if (
        dimensionCount > MAX_REQUESTED_DIMENSIONS ||
            buffer.readableBytes() < dimensionCount * Int.SIZE_BYTES
    ) {
      return
    }
    request = TpsRequest(requestId, List(dimensionCount) { buffer.readInt() }.distinct())
  }

  override fun toBytes(buffer: ByteBuf) {
    val request = checkNotNull(request) { "Cannot encode an invalid TPS request" }
    buffer.writeInt(TPS_PROTOCOL_VERSION)
    buffer.writeLong(request.requestId)
    buffer.writeByte(request.dimensionIds.size)
    request.dimensionIds.forEach(buffer::writeInt)
  }
}

data class TpsRequest(
    val requestId: Long,
    val dimensionIds: List<Int>,
)
