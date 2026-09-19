package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.time.Duration

/**
 * Sits between the map and the tree like a queue in front of a database: the map publishes what
 * it currently sees as often as it likes, the broker keeps only the newest view per tile, serves
 * that view for live rendering, and commits to the tree on a schedule. A tile's first sighting is
 * committed at the next tick; afterwards each tile is committed at most once per [interval], so a
 * minute of block-by-block building becomes one version, and none if the tile ended up looking the
 * same.
 */
class ObservationBroker(
    private val sink: (Commit) -> Unit,
    private val interval: Duration = Duration.ofMinutes(1),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Every due tile, stamped with the commit epoch. */
    class Commit(val epoch: Long, val tiles: Map<TileKey, TileRecord>)

    private class Staged(var pending: TileRecord?, var committed: TileRecord?, var committedAt: Long) {
        /** Drops a pending view identical to the committed one on the way, so it never commits. */
        fun isDue(now: Long, force: Boolean, intervalMillis: Long): Boolean {
            val view = pending ?: return false
            if (committed?.sameFacts(view) == true) {
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

    /**
     * Records the current look of a tile; cheap, safe to call every tick. Returns false when the
     * tile already looked exactly like this, so callers can skip invalidating anything.
     */
    @Synchronized
    fun observe(key: TileKey, view: TileRecord): Boolean {
        val staged = tiles.getOrPut(key) { Staged(null, null, 0L) }
        val current = staged.pending ?: staged.committed
        if (current != null && current.sameFacts(view)) return false
        staged.pending = view
        return true
    }

    /** The newest observed view of a tile for live rendering; null if never seen this session. */
    @Synchronized
    fun latest(key: TileKey): TileRecord? = tiles[key]?.let { it.pending ?: it.committed }

    @Synchronized fun pendingCount(): Int = tiles.values.count { it.pending != null }

    /** Commits tiles whose interval elapsed (or that were never committed); returns how many. */
    fun commitDue(): Int = commit(force = false)

    /** Commits every pending tile, e.g. on world unload or before close. */
    fun commitAll(): Int = commit(force = true)

    private fun commit(force: Boolean): Int {
        val now = clock()
        val batch = HashMap<TileKey, TileRecord>()
        val epoch: Long
        synchronized(this) {
            val due = tiles.filterValues { it.isDue(now, force, interval.toMillis()) }.toList()
            if (due.isEmpty()) return 0
            // Wall-clock epochs, kept strictly increasing even if two commits share a millisecond.
            epoch = maxOf(now, lastEpoch + 1)
            lastEpoch = epoch
            for ((key, staged) in due) {
                val view = checkNotNull(staged.pending).withEpoch(epoch)
                staged.committed = view
                staged.committedAt = now
                staged.pending = null
                batch[key] = view
            }
        }
        sink(Commit(epoch, batch))
        return batch.size
    }

    /** Aligns the epoch sequence with a tree that already has history. */
    @Synchronized
    fun startAfter(epoch: Long) {
        lastEpoch = maxOf(lastEpoch, epoch)
    }
}
