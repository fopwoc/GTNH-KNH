package io.github.fopwoc.mods.hotspot.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSnapshotPartsTest {
  private fun chunk(x: Int, listed: Int) =
      ChunkProfile(
          chunkX = x,
          chunkZ = 0,
          tileEntityMs = 1.0,
          entityMs = 0.0,
          tileEntityCount = listed,
          entityCount = 0,
          tileEntities =
              (0 until listed).map { TileEntityProfile(x * 16, 60, it, 0.1, "Name $it", "Cls") },
      )

  @Test
  fun splitAndAssembleRoundTripsAcrossDimensions() {
    val snapshot =
        ProfileSnapshot(
            requestId = 5,
            takenAtEpochMillis = 123,
            durationTicks = 60,
            dimensions =
                listOf(
                    DimensionProfile(0, "Overworld", 20.0, (0 until 40).map { chunk(it, 30) }),
                    DimensionProfile(-1, "Nether", 3.0, listOf(chunk(1, 2))),
                    DimensionProfile(7, "Twilight", 0.0, emptyList()),
                ),
        )

    val parts = ProfileSnapshotParts.split(snapshot, maxBytes = 4_000)
    assertTrue(parts.size > 3, "expected paging, got ${parts.size} parts")
    assertEquals(parts.indices.toList(), parts.map { it.partIndex })
    assertEquals(listOf(true), parts.map { it.isLast }.distinct().filter { it })
    assertTrue(parts.last().isLast)

    val assembler = ProfileSnapshotParts.Assembler()
    var assembled: ProfileSnapshot? = null
    parts.forEach { part ->
      assertNull(assembled)
      assembled = assembler.accept(part, expectedRequestId = 5)
    }
    assertEquals(snapshot, assembled)
  }

  @Test
  fun emptySnapshotStillProducesOneLastPart() {
    val parts = ProfileSnapshotParts.split(ProfileSnapshot(1, 0, 20, emptyList()))

    assertEquals(1, parts.size)
    assertTrue(parts.single().isLast)
    assertNull(parts.single().dimension)
    assertEquals(
        ProfileSnapshot(1, 0, 20, emptyList()),
        ProfileSnapshotParts.Assembler().accept(parts.single(), expectedRequestId = 1),
    )
  }

  @Test
  fun assemblerIgnoresPartsOfOtherRequests() {
    val assembler = ProfileSnapshotParts.Assembler()
    val stale = ProfileSnapshotPart(1, 0, 20, 0, true, null)

    assertNull(assembler.accept(stale, expectedRequestId = 2))
  }

  @Test
  fun oversizedChunkListsAreTrimmedOnTheWire() {
    val snapshot =
        ProfileSnapshot(
            1,
            0,
            20,
            listOf(DimensionProfile(0, "Overworld", 1.0, listOf(chunk(0, 700)))),
        )

    val parts = ProfileSnapshotParts.split(snapshot)

    assertEquals(512, parts.single().dimension!!.chunks.single().tileEntities.size)
  }
}
