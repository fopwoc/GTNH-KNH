package io.github.fopwoc.mods.tabtps.protocol

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TpsMessageCodecTest {
  @Test
  fun requestRoundTrips() {
    val buffer = Unpooled.buffer()
    TpsRequestMessage(requestId = 42, includeAllDimensions = true).toBytes(buffer)

    val decoded = TpsRequestMessage().also { it.fromBytes(buffer) }

    assertEquals(TPS_PROTOCOL_VERSION, decoded.protocolVersion)
    assertEquals(42, decoded.requestId)
    assertTrue(decoded.includeAllDimensions)
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

    assertEquals(TPS_PROTOCOL_VERSION, decoded.protocolVersion)
    assertEquals(expected, decoded.snapshot)
    assertFalse(buffer.isReadable)
  }
}
