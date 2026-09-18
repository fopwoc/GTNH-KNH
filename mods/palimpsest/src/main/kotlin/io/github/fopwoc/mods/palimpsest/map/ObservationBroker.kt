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
 *
 * A view has one byte array per channel (colors, biomes, ...); all channels of a tile commit
 * together under one epoch, and the store drops the channels that did not change.
 */
class ObservationBroker(
    private val channels: Int,
    private val sink: (Commit) -> Unit,
    private val interval: Duration = Duration.ofMinutes(1),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Layers per channel, all at the same epoch. */
    class Commit(val epoch: Long, val layers: List<List<TileLayer>>)

    private class Staged(
        var pending: Array<ByteArray>?,
        var committed: Array<ByteArray>?,
        var committedAt: Long,
    ) {
        /** Drops a pending view identical to the committed one on the way, so it never commits. */
        fun isDue(now: Long, force: Boolean, intervalMillis: Long): Boolean {
            val view = pending ?: return false
            if (committed?.let { same(it, view) } == true) {
                pending = null
                return false
            }
            return force || committed == null || now - committedAt >= intervalMillis
        }
    }

    private val tiles = HashMap<TileKey, Staged>()
    private var lastEpoch = -1L

    init {
        require(channels > 0 && !interval.isNegative)
    }

    /**
     * Records the current look of a tile; cheap, safe to call every tick. Returns false when the
     * tile already looked exactly like this, so callers can skip invalidating anything.
     */
    @Synchronized
    fun observe(key: TileKey, view: Array<ByteArray>): Boolean {
        require(view.size == channels && view.all { it.size == TileLayer.PIXELS })
        val staged = tiles.getOrPut(key) { Staged(null, null, 0L) }
        val current = staged.pending ?: staged.committed
        if (current != null && same(current, view)) return false
        staged.pending = Array(channels) { view[it].copyOf() }
        return true
    }

    /** The newest observed bytes of one channel for live rendering; null if never seen. */
    @Synchronized
    fun latest(key: TileKey, channel: Int): ByteArray? =
        tiles[key]?.let { it.pending ?: it.committed }?.get(channel)

    @Synchronized fun pendingCount(): Int = tiles.values.count { it.pending != null }

    /** Commits tiles whose interval elapsed (or that were never committed); returns how many. */
    fun commitDue(): Int = commit(force = false)

    /** Commits every pending tile, e.g. on world unload or before close. */
    fun commitAll(): Int = commit(force = true)

    private fun commit(force: Boolean): Int {
        val now = clock()
        val layers = List(channels) { ArrayList<TileLayer>() }
        val count: Int
        val epoch: Long
        synchronized(this) {
            val due = tiles.filterValues { it.isDue(now, force, interval.toMillis()) }.toList()
            if (due.isEmpty()) return 0
            // Wall-clock epochs, kept strictly increasing even if two commits share a millisecond.
            epoch = maxOf(now, lastEpoch + 1)
            lastEpoch = epoch
            for ((key, staged) in due) {
                val view = checkNotNull(staged.pending)
                staged.committed = view
                staged.committedAt = now
                staged.pending = null
                for (channel in 0 until channels) layers[channel] +=
                    TileLayer.full(key, epoch, view[channel])
            }
            count = due.size
        }
        sink(Commit(epoch, layers))
        return count
    }

    private companion object {
        fun same(a: Array<ByteArray>, b: Array<ByteArray>): Boolean =
            a.indices.all { a[it].contentEquals(b[it]) }
    }
}
