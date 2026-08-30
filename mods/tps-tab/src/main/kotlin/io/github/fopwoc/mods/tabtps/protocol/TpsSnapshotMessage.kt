package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

class TpsSnapshotMessage() : IMessage {
  var protocolVersion: Int = TPS_PROTOCOL_VERSION
    private set

  var snapshot: TpsSnapshot = EMPTY_SNAPSHOT
    private set

  constructor(snapshot: TpsSnapshot) : this() {
    this.snapshot = snapshot
  }

  override fun fromBytes(buffer: ByteBuf) {
    protocolVersion = buffer.readInt()
    val requestId = buffer.readLong()
    val server = buffer.readMetrics()
    val currentDimensionId = buffer.readInt()
    val dimensionCount = buffer.readUnsignedShort()
    require(dimensionCount <= MAX_DIMENSIONS_PER_SNAPSHOT) {
      "TPS snapshot contains too many dimensions: $dimensionCount"
    }

    snapshot =
        TpsSnapshot(
            requestId = requestId,
            server = server,
            currentDimensionId = currentDimensionId,
            dimensions =
                List(dimensionCount) {
                  DimensionTpsMetrics(
                      dimensionId = buffer.readInt(),
                      dimensionName = ByteBufUtils.readUTF8String(buffer),
                      metrics = buffer.readMetrics(),
                  )
                },
        )
  }

  override fun toBytes(buffer: ByteBuf) {
    buffer.writeInt(protocolVersion)
    buffer.writeLong(snapshot.requestId)
    buffer.writeMetrics(snapshot.server)
    buffer.writeInt(snapshot.currentDimensionId)

    val dimensions = snapshot.dimensions.take(MAX_DIMENSIONS_PER_SNAPSHOT)
    buffer.writeShort(dimensions.size)
    dimensions.forEach { dimension ->
      buffer.writeInt(dimension.dimensionId)
      ByteBufUtils.writeUTF8String(
          buffer,
          dimension.dimensionName.take(MAX_DIMENSION_NAME_LENGTH),
      )
      buffer.writeMetrics(dimension.metrics)
    }
  }

  private fun ByteBuf.readMetrics(): TpsMetrics =
      TpsMetrics(
          tps = readDouble(),
          mspt = readDouble(),
      )

  private fun ByteBuf.writeMetrics(metrics: TpsMetrics) {
    writeDouble(metrics.tps)
    writeDouble(metrics.mspt)
  }

  private companion object {
    val EMPTY_SNAPSHOT =
        TpsSnapshot(
            requestId = 0,
            server = TpsMetrics(tps = 0.0, mspt = 0.0),
            currentDimensionId = 0,
            dimensions = emptyList(),
        )
  }
}
