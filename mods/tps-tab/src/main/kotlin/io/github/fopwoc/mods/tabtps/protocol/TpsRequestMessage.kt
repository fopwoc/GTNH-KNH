package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

class TpsRequestMessage() : IMessage {
  var protocolVersion: Int = TPS_PROTOCOL_VERSION
    private set

  var requestId: Long = 0
    private set

  var dimensionIds: List<Int> = emptyList()
    private set

  constructor(requestId: Long, dimensionIds: List<Int>) : this() {
    this.requestId = requestId
    this.dimensionIds = dimensionIds.distinct().take(MAX_REQUESTED_DIMENSIONS)
  }

  override fun fromBytes(buffer: ByteBuf) {
    protocolVersion = buffer.readInt()
    requestId = buffer.readLong()
    val dimensionCount = buffer.readUnsignedByte().toInt()
    require(dimensionCount <= MAX_REQUESTED_DIMENSIONS) {
      "TPS request contains too many dimensions: $dimensionCount"
    }
    dimensionIds = List(dimensionCount) { buffer.readInt() }.distinct()
  }

  override fun toBytes(buffer: ByteBuf) {
    buffer.writeInt(protocolVersion)
    buffer.writeLong(requestId)
    val dimensions = dimensionIds.distinct().take(MAX_REQUESTED_DIMENSIONS)
    buffer.writeByte(dimensions.size)
    dimensions.forEach(buffer::writeInt)
  }
}
