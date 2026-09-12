package io.github.fopwoc.mods.hotspot.protocol

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/**
 * Client → server: start (or join) a profiling run.
 *
 * Decoding never throws: a foreign protocol version or a truncated payload leaves [request] null so
 * the handler can ignore it instead of FML terminating the connection.
 */
class ProfileRequestMessage() : IMessage {
  var request: ProfileRequest? = null
    private set

  constructor(request: ProfileRequest) : this() {
    this.request = request
  }

  override fun fromBytes(buffer: ByteBuf) {
    request = null
    if (buffer.readableBytes() < Int.SIZE_BYTES || buffer.readInt() != HOTSPOT_PROTOCOL_VERSION) {
      return
    }
    if (buffer.readableBytes() < Long.SIZE_BYTES + Int.SIZE_BYTES) {
      return
    }
    val requestId = buffer.readLong()
    val durationTicks = buffer.readInt()
    if (durationTicks !in 1..MAX_DURATION_TICKS) {
      return
    }
    request = ProfileRequest(requestId, durationTicks)
  }

  override fun toBytes(buffer: ByteBuf) {
    val request = checkNotNull(request) { "Cannot encode an invalid profile request" }
    buffer.writeInt(HOTSPOT_PROTOCOL_VERSION)
    buffer.writeLong(request.requestId)
    buffer.writeInt(request.durationTicks.coerceIn(1, MAX_DURATION_TICKS))
  }
}
