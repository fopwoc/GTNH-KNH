package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.time.Duration

/**
 * Sits between the map and the history like a queue in front of a database: the map publishes what
 * it currently sees as often as it likes, the broker keeps only the newest view per tile, serves
 * that view for live rendering, and commits to the store on a schedule. A tile's first sighting is
 * committed at the next tick; afterwards each tile is committed at most once per [interval], so a
 * minute of block-by-block building becomes one layer, and none if the tile ended up looking the
 * same.
 */
class ObservationBroker(
    private val sink: (List<TileLayer>) -> Unit,
    private val interval: Duration = Duration.ofMinutes(1),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Staged(
        var pending: ByteArray?,
        var committed: ByteArray?,
        var committedAt: Long,
    ) {
        /** Drops a pending view identical to the committed one on the way, so it never commits. */
        fun isDue(now: Long, force: Boolean, intervalMillis: Long): Boolean {
            val view = pending ?: return false
            if (committed?.contentEquals(view) == true) {
                pending = null
                return false
            }
            return force || committed == null || now - committedAt >= intervalMillis
        }
    }

    private val tiles = HashMap<TileKey, Staged>()
    private var lastEpoch = -1L

    init {
        require(!interval.isNegative)
    }

    /** Records the current look of a tile; cheap, safe to call every tick. */
    @Synchronized
    fun observe(key: TileKey, colors: ByteArray) {
        require(colors.size == TileLayer.PIXELS)
        val staged = tiles.getOrPut(key) { Staged(null, null, 0L) }
        staged.pending = colors.copyOf()
    }

    /** The newest observed colors for live rendering, committed or not; null if never seen. */
    @Synchronized
    fun latest(key: TileKey): ByteArray? = tiles[key]?.let { it.pending ?: it.committed }

    @Synchronized fun pendingCount(): Int = tiles.values.count { it.pending != null }

    /** Commits tiles whose interval elapsed (or that were never committed); returns how many. */
    fun commitDue(): Int = commit(force = false)

    /** Commits every pending tile, e.g. on world unload or before close. */
    fun commitAll(): Int = commit(force = true)

    private fun commit(force: Boolean): Int {
        val now = clock()
        val layers: List<TileLayer>
        synchronized(this) {
            val due = tiles.filterValues { it.isDue(now, force, interval.toMillis()) }.toList()
            if (due.isEmpty()) return 0
            // Wall-clock epochs, kept strictly increasing even if two commits share a millisecond.
            val epoch = maxOf(now, lastEpoch + 1)
            lastEpoch = epoch
            layers = due.map { (key, staged) ->
                val colors = checkNotNull(staged.pending)
                staged.committed = colors
                staged.committedAt = now
                staged.pending = null
                TileLayer.full(key, epoch, colors)
            }
        }
        sink(layers)
        return layers.size
    }
}
