package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MalformedMessageException
import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HotspotCodecTest {
    private fun <P : Any> MessageCodec<P>.bytes(payload: P): ByteArray =
        MessageWriter().also { encode(it, payload) }.toByteArray()

    private fun <P : Any> MessageCodec<P>.roundTrip(payload: P): P {
        val reader = MessageReader(bytes(payload))
        return decode(reader).also { assertTrue(reader.isExhausted) }
    }

    @Test
    fun requestAndStatusRoundTrip() {
        assertEquals(ProfileRequest(7, 100), ProfileRequestCodec.roundTrip(ProfileRequest(7, 100)))
        val status = ProfileStatusUpdate(9, ProfileStatus.STARTED, 60, 300)
        assertEquals(status, ProfileStatusCodec.roundTrip(status))
    }

    @Test
    fun snapshotPartRoundTripsWithSharedNames() {
        val chunk =
            ChunkProfile(
                chunkX = -78,
                chunkZ = 24,
                tileEntityMs = 4.125,
                entityMs = 0.5,
                tileEntityCount = 37,
                entityCount = 12,
                tileEntities =
                    listOf(
                        TileEntityProfile(
                            -1241,
                            68,
                            391,
                            2.25,
                            "Large Steam Turbine",
                            "GT_MetaTileEntity",
                        ),
                        TileEntityProfile(-1240, 68, 391, 0.5, "Item Pipe", "GT_MetaTileEntity"),
                        TileEntityProfile(-1239, 68, 391, 0.25, "Item Pipe", "GT_MetaTileEntity"),
                    ),
            )
        val part =
            ProfileSnapshotPart(
                requestId = 3,
                takenAtEpochMillis = 1_700_000_000_000,
                durationTicks = 100,
                partIndex = 2,
                isLast = true,
                dimension = DimensionPage(0, "Overworld", 38.5, listOf(chunk)),
            )

        assertEquals(part, ProfileSnapshotPartCodec.roundTrip(part))
        assertEquals(
            ProfileSnapshotPart(1, 2, 3, 0, true, null),
            ProfileSnapshotPartCodec.roundTrip(ProfileSnapshotPart(1, 2, 3, 0, true, null)),
        )
    }

    @Test
    fun truncatedAndOutOfRangePayloadsAreRejected() {
        val truncated = MessageWriter().long(1).long(2).int(20).int(0).byte(2).toByteArray()
        assertFailsWith<MalformedMessageException> {
            ProfileSnapshotPartCodec.decode(MessageReader(truncated))
        }

        val outOfRange = MessageWriter().long(1).int(MAX_DURATION_TICKS + 1).toByteArray()
        assertFailsWith<MalformedMessageException> {
            ProfileRequestCodec.decode(MessageReader(outOfRange))
        }
    }

    @Test
    fun encodedPartsStayUnderTheVanillaPayloadLimit() {
        val chunks =
            (0 until 300).map { index ->
                ChunkProfile(
                    chunkX = index,
                    chunkZ = -index,
                    tileEntityMs = 1.0,
                    entityMs = 0.0,
                    tileEntityCount = 128,
                    entityCount = 0,
                    tileEntities =
                        (0 until 128).map { i ->
                            TileEntityProfile(
                                index * 16 + i % 16,
                                64,
                                i / 16,
                                0.01,
                                "Machine $i",
                                "Class${i % 7}",
                            )
                        },
                )
            }
        val parts =
            ProfileSnapshotParts.split(
                ProfileSnapshot(1, 0, 100, listOf(DimensionProfile(0, "Overworld", 10.0, chunks)))
            )

        assertTrue(parts.size > 1)
        parts.forEach { part ->
            val size = ProfileSnapshotPartCodec.bytes(part).size
            assertTrue(size < 32_000, "part ${part.partIndex} is $size bytes")
        }
    }
}
