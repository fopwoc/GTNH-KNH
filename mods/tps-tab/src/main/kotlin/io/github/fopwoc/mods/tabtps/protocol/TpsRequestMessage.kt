package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

class TpsRequestMessage() : IMessage {
  var protocolVersion: Int = TPS_PROTOCOL_VERSION
    private set

  var requestId: Long = 0
    private set

  var includeAllDimensions: Boolean = false
    private set

  constructor(requestId: Long, includeAllDimensions: Boolean) : this() {
    this.requestId = requestId
    this.includeAllDimensions = includeAllDimensions
  }

  override fun fromBytes(buffer: ByteBuf) {
    protocolVersion = buffer.readInt()
    requestId = buffer.readLong()
    includeAllDimensions = buffer.readBoolean()
  }

  override fun toBytes(buffer: ByteBuf) {
    buffer.writeInt(protocolVersion)
    buffer.writeLong(requestId)
    buffer.writeBoolean(includeAllDimensions)
  }
}
