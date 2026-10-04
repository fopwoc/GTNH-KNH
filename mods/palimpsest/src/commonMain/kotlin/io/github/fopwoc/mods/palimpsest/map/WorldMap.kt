package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.history.HistoryTiles
import io.github.fopwoc.mods.palimpsest.history.NoHistory
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.time.Duration

/**
 * The map of one dimension behind one door. The scanner feeds it the live look of chunks with
 * [observe] and stages their full snapshots into [history]; [tick] once a second from the game
 * thread commits history on its interval; [view] draws, and [close] goes with the dimension.
 */
class WorldMap(
    blocks: BlockTable,
    val history: DimensionHistory?,
    private val worldTime: () -> Long,
    grassTint: (Int) -> Int = { MapPageStore.WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { MapPageStore.WHITE },
    commitInterval: () -> Duration = { Duration.ofMinutes(1) },
    onChanged: () -> Unit = {},
    sampleBudget: PageSampleBudget = PageSampleBudget(),
) : AutoCloseable {
    private val logger = logger<WorldMap>()
    val store =
        MapPageStore(
            blocks,
            history?.let(::HistoryTiles) ?: NoHistory,
            grassTint,
            foliageTint,
            waterTint,
            commitInterval,
            sampleBudget = sampleBudget,
        )
    val view = MapView(store, onChanged = onChanged)

    /** The current 16×16 view of a chunk; as often as the mod likes. */
    fun observe(chunkX: Int, chunkZ: Int, view: TileRecord, source: Any = directSource) =
        store.observe(TileKey(chunkX, chunkZ), view, source)

    /** Once a second on the game thread. */
    fun tick() {
        store.commitDue()
        history?.tick(worldTime())
    }

    /** Commits what is staged now; for an explicit save. */
    fun flush() {
        history?.commitNow(worldTime())
    }

    override fun close() {
        view.close()
        history?.commitNow(worldTime())
        store.close()
        logger.info("World map closed")
    }

    companion object {
        private val directSource = Any()
    }
}
