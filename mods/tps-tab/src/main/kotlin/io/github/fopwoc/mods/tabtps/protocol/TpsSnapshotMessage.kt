package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.github.fopwoc.mods.framework.network.writeUtf8
import io.netty.buffer.ByteBuf

/** Server → client TPS snapshot. */
class TpsSnapshotMessage() : VersionedMessage<TpsSnapshot>(TPS_PROTOCOL_VERSION) {
  constructor(snapshot: TpsSnapshot) : this() {
    payload = snapshot
  }

  override fun encode(buffer: ByteBuf, payload: TpsSnapshot) {
    buffer.writeLong(payload.requestId)
    buffer.writeMetrics(payload.server)
    buffer.writeInt(payload.currentDimensionId)

    val dimensions = payload.dimensions.take(MAX_DIMENSIONS_PER_SNAPSHOT)
    buffer.writeShort(dimensions.size)
    dimensions.forEach { dimension ->
      buffer.writeInt(dimension.dimensionId)
      buffer.writeUtf8(dimension.dimensionName, MAX_DIMENSION_NAME_LENGTH)
      buffer.writeMetrics(dimension.metrics)
    }
  }

  override fun decode(reader: MessageReader): TpsSnapshot {
    val requestId = reader.long()
    val server = reader.readMetrics()
    val currentDimensionId = reader.int()
    val dimensions =
        reader.list(MAX_DIMENSIONS_PER_SNAPSHOT, { unsignedShort() }) {
          DimensionTpsMetrics(int(), utf8(MAX_DIMENSION_NAME_LENGTH), readMetrics())
        }
    return TpsSnapshot(requestId, server, currentDimensionId, dimensions)
  }

  private fun MessageReader.readMetrics(): TpsMetrics = TpsMetrics(tps = double(), mspt = double())

  private fun ByteBuf.writeMetrics(metrics: TpsMetrics) {
    writeDouble(metrics.tps)
    writeDouble(metrics.mspt)
  }
}
