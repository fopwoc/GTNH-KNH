package io.github.fopwoc.mods.tabtps.protocol

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TpsMessageCodecTest {
  @Test
  fun requestRoundTrips() {
    val buffer = Unpooled.buffer()
    TpsRequestMessage(requestId = 42, dimensionIds = listOf(0, -1, 0, 7)).toBytes(buffer)

    val decoded = TpsRequestMessage().also { it.fromBytes(buffer) }

    assertEquals(TpsRequest(42, listOf(0, -1, 7)), decoded.payload)
    assertFalse(buffer.isReadable)
  }

  @Test
  fun foreignProtocolVersionDecodesAsInvalidInsteadOfThrowing() {
    val buffer = Unpooled.buffer()
    buffer.writeInt(TPS_PROTOCOL_VERSION + 1)
    buffer.writeLong(1)
    buffer.writeByte(200)

    assertNull(TpsRequestMessage().also { it.fromBytes(buffer) }.payload)
    assertNull(TpsSnapshotMessage().also { it.fromBytes(buffer.resetReaderIndex()) }.payload)
  }

  @Test
  fun truncatedOrOversizedPayloadsDecodeAsInvalid() {
    val truncatedRequest = Unpooled.buffer()
    truncatedRequest.writeInt(TPS_PROTOCOL_VERSION)
    truncatedRequest.writeLong(1)
    truncatedRequest.writeByte(3)
    truncatedRequest.writeInt(0)
    assertNull(TpsRequestMessage().also { it.fromBytes(truncatedRequest) }.payload)

    val tooManyDimensions = Unpooled.buffer()
    TpsRequestMessage(requestId = 1, dimensionIds = emptyList()).toBytes(tooManyDimensions)
    tooManyDimensions.setByte(Int.SIZE_BYTES + Long.SIZE_BYTES, MAX_REQUESTED_DIMENSIONS + 1)
    assertNull(TpsRequestMessage().also { it.fromBytes(tooManyDimensions) }.payload)

    val truncatedSnapshot = Unpooled.buffer()
    TpsSnapshotMessage(
            TpsSnapshot(
                requestId = 1,
                server = TpsMetrics(20.0, 10.0),
                currentDimensionId = 0,
                dimensions = listOf(DimensionTpsMetrics(0, "Overworld", TpsMetrics(20.0, 10.0))),
            )
        )
        .toBytes(truncatedSnapshot)
    truncatedSnapshot.writerIndex(truncatedSnapshot.writerIndex() - 3)
    assertNull(TpsSnapshotMessage().also { it.fromBytes(truncatedSnapshot) }.payload)
  }

  @Test
  fun snapshotRoundTrips() {
    val expected =
        TpsSnapshot(
            requestId = 7,
            server = TpsMetrics(tps = 19.75, mspt = 50.63),
            currentDimensionId = 0,
            dimensions =
                listOf(
                    DimensionTpsMetrics(
                        dimensionId = 0,
                        dimensionName = "Overworld",
                        metrics = TpsMetrics(tps = 19.75, mspt = 32.5),
                    ),
                    DimensionTpsMetrics(
                        dimensionId = -1,
                        dimensionName = "Nether",
                        metrics = TpsMetrics(tps = 19.75, mspt = 11.25),
                    ),
                ),
        )
    val buffer = Unpooled.buffer()
    TpsSnapshotMessage(expected).toBytes(buffer)

    val decoded = TpsSnapshotMessage().also { it.fromBytes(buffer) }

    assertEquals(expected, decoded.payload)
    assertFalse(buffer.isReadable)
  }
}
