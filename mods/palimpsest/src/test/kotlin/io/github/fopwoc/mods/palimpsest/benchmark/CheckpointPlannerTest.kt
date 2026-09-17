package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckpointPlannerTest {
  @Test
  fun checkpointsOnlyTheTilesNeededToMeetTheBudget() {
    val costs =
        listOf(
            CheckpointPlanner.TileCost(TileKey(0, 0), 1200),
            CheckpointPlanner.TileCost(TileKey(1, 0), 900),
            CheckpointPlanner.TileCost(TileKey(2, 0), 100),
        )
    assertEquals(emptyList(), CheckpointPlanner.select(costs, 2200))
    assertEquals(listOf(TileKey(0, 0)), CheckpointPlanner.select(costs, 1200))
    assertEquals(listOf(TileKey(0, 0), TileKey(1, 0)), CheckpointPlanner.select(costs, 256))
  }
}
