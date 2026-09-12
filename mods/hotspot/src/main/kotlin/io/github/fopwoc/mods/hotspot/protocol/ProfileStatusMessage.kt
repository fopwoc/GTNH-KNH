package io.github.fopwoc.mods.hotspot.protocol

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/** Server → client: whether the run started and how long it still takes. Never throws on decode. */
class ProfileStatusMessage() : IMessage {
  var requestId: Long = 0
    private set

  var status: ProfileStatus? = null
    private set

  var remainingTicks: Int = 0
    private set

  constructor(requestId: Long, status: ProfileStatus, remainingTicks: Int) : this() {
    this.requestId = requestId
    this.status = status
    this.remainingTicks = remainingTicks
  }

  override fun fromBytes(buffer: ByteBuf) {
    status = null
    if (buffer.readableBytes() < Int.SIZE_BYTES || buffer.readInt() != HOTSPOT_PROTOCOL_VERSION) {
      return
    }
    if (buffer.readableBytes() < Long.SIZE_BYTES + 1 + Int.SIZE_BYTES) {
      return
    }
    requestId = buffer.readLong()
    val ordinal = buffer.readUnsignedByte().toInt()
    remainingTicks = buffer.readInt()
    status = ProfileStatus.entries.getOrNull(ordinal)
  }

  override fun toBytes(buffer: ByteBuf) {
    val status = checkNotNull(status) { "Cannot encode an invalid profile status" }
    buffer.writeInt(HOTSPOT_PROTOCOL_VERSION)
    buffer.writeLong(requestId)
    buffer.writeByte(status.ordinal)
    buffer.writeInt(remainingTicks)
  }
}
