package io.github.fopwoc.mods.tabtps.protocol

import io.github.fopwoc.mods.framework.network.MalformedMessageException
import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TpsCodecTest {
    private fun <P : Any> MessageCodec<P>.bytes(payload: P): ByteArray = MessageWriter().also { encode(it, payload) }.toByteArray()

    private fun <P : Any> MessageCodec<P>.roundTrip(payload: P): P {
        val reader = MessageReader(bytes(payload))
        return decode(reader).also { assertTrue(reader.isExhausted) }
    }

    private val snapshot =
        TpsSnapshot(
            requestId = 7,
            server = TpsMetrics(tps = 19.75, mspt = 50.63),
            currentDimensionId = "0",
            dimensions =
                listOf(
                    DimensionTpsMetrics("0", "Overworld", TpsMetrics(tps = 19.75, mspt = 32.5)),
                    DimensionTpsMetrics("-1", "Nether", TpsMetrics(tps = 19.75, mspt = 11.25)),
                ),
        )

    @Test
    fun requestRoundTripsWithoutDuplicateDimensions() {
        assertEquals(TpsRequest(42, listOf("0", "-1", "7")), TpsRequestCodec.roundTrip(TpsRequest(42, listOf("0", "-1", "0", "7"))))
    }

    @Test
    fun snapshotRoundTrips() {
        assertEquals(snapshot, TpsSnapshotCodec.roundTrip(snapshot))
    }

    @Test
    fun resourceKeyDimensionsRoundTrip() {
        val request = TpsRequest(43, listOf("minecraft:overworld", "mymod:mining"))
        val modernSnapshot = snapshot.copy(
            currentDimensionId = "minecraft:overworld",
            dimensions = listOf(DimensionTpsMetrics("mymod:mining", "Mining", TpsMetrics(20.0, 4.0))),
        )
        assertEquals(request, TpsRequestCodec.roundTrip(request))
        assertEquals(modernSnapshot, TpsSnapshotCodec.roundTrip(modernSnapshot))
    }

    @Test
    fun truncatedOrOversizedPayloadsAreRejected() {
        val truncatedRequest = MessageWriter().long(1).byte(3).utf8("0", MAX_DIMENSION_ID_LENGTH).toByteArray()
        assertFailsWith<MalformedMessageException> { TpsRequestCodec.decode(MessageReader(truncatedRequest)) }

        val tooManyDimensions = MessageWriter().long(1).byte(MAX_REQUESTED_DIMENSIONS + 1).toByteArray()
        assertFailsWith<MalformedMessageException> { TpsRequestCodec.decode(MessageReader(tooManyDimensions)) }

        val truncatedSnapshot = TpsSnapshotCodec.bytes(snapshot).let { it.copyOf(it.size - 3) }
        assertFailsWith<MalformedMessageException> { TpsSnapshotCodec.decode(MessageReader(truncatedSnapshot)) }
    }
}
