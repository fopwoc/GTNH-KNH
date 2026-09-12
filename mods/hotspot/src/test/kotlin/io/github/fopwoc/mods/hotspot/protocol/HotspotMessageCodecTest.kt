package io.github.fopwoc.mods.hotspot.protocol

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotspotMessageCodecTest {
  @Test
  fun requestRoundTrips() {
    val buffer = Unpooled.buffer()
    ProfileRequestMessage(ProfileRequest(7, 100)).toBytes(buffer)

    val decoded = ProfileRequestMessage().also { it.fromBytes(buffer) }

    assertEquals(ProfileRequest(7, 100), decoded.payload)
    assertFalse(buffer.isReadable)
  }

  @Test
  fun statusRoundTrips() {
    val buffer = Unpooled.buffer()
    ProfileStatusMessage(ProfileStatusUpdate(9, ProfileStatus.STARTED, 60)).toBytes(buffer)

    val decoded = ProfileStatusMessage().also { it.fromBytes(buffer) }

    assertEquals(ProfileStatusUpdate(9, ProfileStatus.STARTED, 60), decoded.payload)
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
    val buffer = Unpooled.buffer()
    ProfileSnapshotPartMessage(part).toBytes(buffer)

    val decoded = ProfileSnapshotPartMessage().also { it.fromBytes(buffer) }.payload

    assertEquals(part, decoded)
    assertFalse(buffer.isReadable)
  }

  @Test
  fun partWithoutDimensionRoundTrips() {
    val part = ProfileSnapshotPart(1, 2, 3, 0, true, null)
    val buffer = Unpooled.buffer()
    ProfileSnapshotPartMessage(part).toBytes(buffer)

    assertEquals(part, ProfileSnapshotPartMessage().also { it.fromBytes(buffer) }.payload)
  }

  @Test
  fun foreignVersionAndTruncatedPayloadsDecodeAsInvalid() {
    val foreign = Unpooled.buffer()
    foreign.writeInt(HOTSPOT_PROTOCOL_VERSION + 1)
    foreign.writeLong(1)
    foreign.writeInt(20)
    assertNull(ProfileRequestMessage().also { it.fromBytes(foreign) }.payload)
    assertNull(ProfileStatusMessage().also { it.fromBytes(foreign.resetReaderIndex()) }.payload)
    assertNull(
        ProfileSnapshotPartMessage().also { it.fromBytes(foreign.resetReaderIndex()) }.payload
    )

    val truncated = Unpooled.buffer()
    truncated.writeInt(HOTSPOT_PROTOCOL_VERSION)
    truncated.writeLong(1)
    truncated.writeLong(2)
    truncated.writeInt(20)
    truncated.writeInt(0)
    truncated.writeByte(2) // has dimension, but nothing follows
    assertNull(ProfileSnapshotPartMessage().also { it.fromBytes(truncated) }.payload)

    val outOfRange = Unpooled.buffer()
    outOfRange.writeInt(HOTSPOT_PROTOCOL_VERSION)
    outOfRange.writeLong(1)
    outOfRange.writeInt(MAX_DURATION_TICKS + 1)
    assertNull(ProfileRequestMessage().also { it.fromBytes(outOfRange) }.payload)
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
    val snapshot =
        ProfileSnapshot(1, 0, 100, listOf(DimensionProfile(0, "Overworld", 10.0, chunks)))

    val parts = ProfileSnapshotParts.split(snapshot)

    assertTrue(parts.size > 1)
    parts.forEach { part ->
      val buffer = Unpooled.buffer()
      ProfileSnapshotPartMessage(part).toBytes(buffer)
      assertTrue(
          buffer.readableBytes() < 32_000,
          "part ${part.partIndex} is ${buffer.readableBytes()} bytes",
      )
    }
  }
}
