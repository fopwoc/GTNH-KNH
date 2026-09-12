package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.hotspot.server.profiler.RawEntitySample
import io.github.fopwoc.mods.hotspot.server.profiler.RawProfile
import io.github.fopwoc.mods.hotspot.server.profiler.RawTileEntitySample
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSnapshotBuilderTest {
  private val limits =
      ProfileSnapshotBuilder.Limits(
          minNanosPerTileEntity = 5_000.0,
          maxListedTileEntitiesPerChunk = 2,
      )

  @Test
  fun groupsByDimensionAndChunkAndOrdersHeaviestFirst() {
    val raw =
        RawProfile(
            tileEntities =
                listOf(
                    RawTileEntitySample(0, 5, 64, 5, 1_000_000.0, "Turbine"),
                    RawTileEntitySample(0, 6, 64, 5, 200_000.0, "Pipe"),
                    RawTileEntitySample(0, 7, 64, 5, 100_000.0, "Pipe"),
                    RawTileEntitySample(0, 8, 64, 5, 1_000.0, "Cable"),
                    RawTileEntitySample(0, -1, 64, 5, 3_000_000.0, "Boiler"),
                    RawTileEntitySample(-1, 0, 64, 0, 50_000.0, "Furnace"),
                ),
            entities = listOf(RawEntitySample(0, 0, 0, 400_000.0)),
            dimensionTickNanos = mapOf(0 to 10_000_000.0, -1 to 500_000.0),
        )

    val snapshot =
        ProfileSnapshotBuilder.build(
            raw = raw,
            requestId = 1,
            takenAtEpochMillis = 0,
            durationTicks = 100,
            limits = limits,
            dimensionName = { if (it == 0) "Overworld" else "Nether" },
            tileEntityName = { "${it.className}@${it.x}" },
        )

    assertEquals(listOf(0, -1), snapshot.dimensions.map { it.id })
    val overworld = snapshot.dimensions[0]
    assertEquals(10.0, overworld.tickMs)
    assertEquals(listOf(-1 to 0, 0 to 0), overworld.chunks.map { it.chunkX to it.chunkZ })

    val origin = overworld.chunks[1]
    assertEquals(4, origin.tileEntityCount)
    assertEquals(1, origin.entityCount)
    assertEquals(1.301, origin.tileEntityMs, 1e-9)
    assertEquals(0.4, origin.entityMs, 1e-9)
    assertEquals(listOf("Turbine@5", "Pipe@6"), origin.tileEntities.map { it.name })
    assertEquals(1.0, origin.tileEntities[0].ms, 1e-9)
    assertEquals("Turbine", origin.tileEntities[0].className)
  }

  @Test
  fun dimensionWithOnlyTickTimingStillAppears() {
    val snapshot =
        ProfileSnapshotBuilder.build(
            raw = RawProfile(emptyList(), emptyList(), mapOf(3 to 2_000_000.0)),
            requestId = 1,
            takenAtEpochMillis = 0,
            durationTicks = 20,
            limits = limits,
            dimensionName = { "DIM$it" },
            tileEntityName = { it.className },
        )

    assertEquals(listOf("DIM3"), snapshot.dimensions.map { it.name })
    assertEquals(2.0, snapshot.dimensions.single().tickMs)
    assertEquals(emptyList(), snapshot.dimensions.single().chunks)
  }
}
