package io.github.fopwoc.mods.tabtps.protocol

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/**
 * Server → client TPS snapshot.
 *
 * Decoding never throws: a foreign protocol version or a malformed payload leaves [snapshot] null
 * so the client keeps showing its "protocol does not match" status instead of being disconnected.
 */
class TpsSnapshotMessage() : IMessage {
  var snapshot: TpsSnapshot? = null
    private set

  constructor(snapshot: TpsSnapshot) : this() {
    this.snapshot = snapshot
  }

  override fun fromBytes(buffer: ByteBuf) {
    snapshot = null
    if (buffer.readableBytes() < Int.SIZE_BYTES || buffer.readInt() != TPS_PROTOCOL_VERSION) {
      return
    }
    if (buffer.readableBytes() < HEADER_BYTES) {
      return
    }
    val requestId = buffer.readLong()
    val server = buffer.readMetrics()
    val currentDimensionId = buffer.readInt()
    val dimensionCount = buffer.readUnsignedShort()
    if (dimensionCount > MAX_DIMENSIONS_PER_SNAPSHOT) {
      return
    }

    val dimensions = ArrayList<DimensionTpsMetrics>(dimensionCount)
    repeat(dimensionCount) {
      if (buffer.readableBytes() < Int.SIZE_BYTES + 1) {
        return
      }
      val dimensionId = buffer.readInt()
      val dimensionName = buffer.readBoundedUtf8() ?: return
      if (buffer.readableBytes() < METRICS_BYTES) {
        return
      }
      dimensions += DimensionTpsMetrics(dimensionId, dimensionName, buffer.readMetrics())
    }

    snapshot =
        TpsSnapshot(
            requestId = requestId,
            server = server,
            currentDimensionId = currentDimensionId,
            dimensions = dimensions,
        )
  }

  override fun toBytes(buffer: ByteBuf) {
    val snapshot = checkNotNull(snapshot) { "Cannot encode an invalid TPS snapshot" }
    buffer.writeInt(TPS_PROTOCOL_VERSION)
    buffer.writeLong(snapshot.requestId)
    buffer.writeMetrics(snapshot.server)
    buffer.writeInt(snapshot.currentDimensionId)

    val dimensions = snapshot.dimensions.take(MAX_DIMENSIONS_PER_SNAPSHOT)
    buffer.writeShort(dimensions.size)
    dimensions.forEach { dimension ->
      buffer.writeInt(dimension.dimensionId)
      ByteBufUtils.writeUTF8String(buffer, dimension.dimensionName.take(MAX_DIMENSION_NAME_LENGTH))
      buffer.writeMetrics(dimension.metrics)
    }
  }

  private fun ByteBuf.readMetrics(): TpsMetrics =
      TpsMetrics(tps = readDouble(), mspt = readDouble())

  private fun ByteBuf.writeMetrics(metrics: TpsMetrics) {
    writeDouble(metrics.tps)
    writeDouble(metrics.mspt)
  }

  /** Mirrors [ByteBufUtils.readUTF8String] (varint length + UTF-8) with a hard size cap. */
  private fun ByteBuf.readBoundedUtf8(): String? {
    val length = ByteBufUtils.readVarInt(this, 2)
    if (length < 0 || length > MAX_DIMENSION_NAME_BYTES || readableBytes() < length) {
      return null
    }
    val bytes = ByteArray(length)
    readBytes(bytes)
    return String(bytes, Charsets.UTF_8)
  }

  private companion object {
    const val METRICS_BYTES = 2 * Long.SIZE_BYTES
    const val HEADER_BYTES = Long.SIZE_BYTES + METRICS_BYTES + Int.SIZE_BYTES + Short.SIZE_BYTES
    const val MAX_DIMENSION_NAME_BYTES = MAX_DIMENSION_NAME_LENGTH * 3
  }
}
