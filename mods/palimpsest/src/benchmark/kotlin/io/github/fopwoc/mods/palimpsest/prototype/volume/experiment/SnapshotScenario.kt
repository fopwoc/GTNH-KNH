package io.github.fopwoc.mods.palimpsest.prototype.volume.experiment

import io.github.fopwoc.mods.palimpsest.prototype.volume.save.LegacySave
import io.github.fopwoc.mods.palimpsest.tree.ByteSink
import io.github.fopwoc.mods.palimpsest.tree.Ref
import io.github.fopwoc.mods.palimpsest.tree.TileCodec
import io.github.fopwoc.mods.palimpsest.tree.TileKey

/**
 * One dimension of a long-lived world as a single first sight: every populated chunk through both
 * stores, with costs split by how long players stayed near each chunk. A snapshot has no history;
 * it shows what the world's present costs, and where the expensive chunks are.
 */
class SnapshotScenario(
    private val save: LegacySave,
    private val dimension: String,
    private val stores: PairedStores,
    private val perCommit: Int = 256,
) {
    enum class Category(val label: String, val fromHours: Double) {
        WILD("flown over, < 5 min", 0.0),
        VISITED("visited, 5 min – 10 h", 5.0 / 60),
        BASE("base, > 10 h", 10.0),
    }

    class Cost {
        var chunks = 0
        var surfaceRecordBytes = 0L
        var volumeBytes = 0L
    }

    class Result(
        val costs: Map<Category, Cost>,
        val surfaceBytes: Long,
        val volumeBytes: Long,
        val lastEpoch: Long,
        val busiest: TileKey,
    )

    fun run(): Result {
        val costs = Category.entries.associateWith { Cost() }
        var epoch = 1L
        var busiest = TileKey(0, 0)
        var busiestTicks = -1L
        for (region in save.regions(dimension)) {
            val chunks = save.chunks(region).sortedBy { morton(it.key.x, it.key.z) }.toList()
            val categories = chunks.associate { saved ->
                if (saved.inhabitedTicks > busiestTicks) {
                    busiestTicks = saved.inhabitedTicks
                    busiest = saved.key
                }
                val hours = saved.inhabitedTicks / TICKS_PER_HOUR
                saved.key to Category.entries.last { hours >= it.fromHours }
            }
            for (group in chunks.chunked(perCommit)) {
                val committed = stores.commit(epoch++, group.associate { it.key to it.volume })
                for ((key, tile) in committed.tiles) {
                    val cost = costs.getValue(categories.getValue(key))
                    cost.chunks++
                    cost.surfaceRecordBytes +=
                        ByteSink(256).also { TileCodec.encodeFull(it, tile, Ref.NULL) }.size
                    cost.volumeBytes += committed.packed[key] ?: 0
                }
            }
        }
        return Result(costs, stores.surfaceBytes(), stores.volumes.bytes, epoch - 1, busiest)
    }

    private companion object {
        const val TICKS_PER_HOUR = 72_000.0
    }
}
