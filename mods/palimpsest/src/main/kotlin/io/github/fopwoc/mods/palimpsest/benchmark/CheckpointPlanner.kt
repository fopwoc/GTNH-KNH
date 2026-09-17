package io.github.fopwoc.mods.palimpsest.benchmark

import io.github.fopwoc.mods.palimpsest.storage.TileKey

/** Checkpoint the most expensive visible tiles until the latest viewport fits its read budget. */
internal object CheckpointPlanner {
  data class TileCost(val key: TileKey, val layersVisited: Int)

  fun select(costs: List<TileCost>, budget: Int): List<TileKey> {
    require(budget >= costs.size)
    var projected = costs.sumOf(TileCost::layersVisited)
    if (projected <= budget) return emptyList()
    val chosen = ArrayList<TileKey>()
    for (cost in costs.sortedByDescending(TileCost::layersVisited)) {
      if (cost.layersVisited <= 1) break
      chosen += cost.key
      projected -= cost.layersVisited - 1
      if (projected <= budget) break
    }
    return chosen
  }
}
