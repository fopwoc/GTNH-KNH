package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.time.Duration

/**
 * Sits between the map and the tree like a queue in front of a database: the map publishes what it
 * currently sees as often as it likes, the broker keeps only the newest view per tile, serves that
 * view for live rendering, and accepts it as history only after a second scan of the same loaded
 * source confirms it. Accepted changes are committed to the tree on [interval], so a minute of
 * block-by-block building becomes one version (none if the tile ended up looking the same) and a
 * minute of exploring becomes one root. Between commits the live view reads the candidate directly,
 * so nothing waits.
 */
class ObservationBroker(
    private val sink: (Commit) -> Unit,
    /** Read at every commit, so a settings change applies without reopening the map. */
    private val interval: () -> Duration = { Duration.ofMinutes(1) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Every due tile, stamped with the commit epoch. */
    class Commit(val epoch: Long, val tiles: Map<TileKey, TileRecord>)

    private class Staged(var candidate: TileRecord?, var committed: TileRecord?) {
        private var candidateSource: Any? = null
        private var confirmed = false

        /**
         * Replaces the live candidate or confirms it on a later scan of the same loaded source. A
         * new source means the chunk was reloaded, so its first observation starts over.
         */
        fun observe(view: TileRecord, source: Any): Boolean {
            candidate?.let { current ->
                if (current.sameFacts(view)) {
                    if (candidateSource === source) confirmed = true
                    else {
                        candidateSource = source
                        confirmed = false
                    }
                    return false
                }
            }
            if (committed?.sameFacts(view) == true) {
                val changed = candidate != null
                candidate = null
                candidateSource = null
                confirmed = false
                return changed
            }
            candidate = view
            candidateSource = source
            confirmed = false
            return true
        }

        fun isDue(): Boolean = candidate != null && confirmed

        fun accept(epoch: Long): TileRecord {
            val accepted = checkNotNull(candidate).withEpoch(epoch)
            committed = accepted
            candidate = null
            candidateSource = null
            confirmed = false
            return accepted
        }
    }

    private val tiles = HashMap<TileKey, Staged>()
    private var lastEpoch = -1L
    private var lastCommitAt = Long.MIN_VALUE
    /** Serializes whole commits (staging and sink), so epochs reach the tree in order. */
    private val committing = Any()

    /**
     * Records the current look of a tile; cheap, safe to call every tick. Returns false when the
     * tile already looked exactly like this, so callers can skip invalidating anything.
     */
    @Synchronized
    fun observe(key: TileKey, view: TileRecord, source: Any = directSource): Boolean {
        val staged = tiles.getOrPut(key) { Staged(null, null) }
        return staged.observe(view, source)
    }

    /** The newest observed view of a tile for live rendering; null if never seen this session. */
    @Synchronized
    fun latest(key: TileKey): TileRecord? = tiles[key]?.let { it.candidate ?: it.committed }

    @Synchronized fun pendingCount(): Int = tiles.values.count { it.candidate != null }

    /** Tiles observed at least once this session. */
    @Synchronized fun seenCount(): Int = tiles.size

    /**
     * Tiles observed but not yet committed, for overlaying on far-zoom pages built from the tree.
     */
    @Synchronized
    fun pending(): Map<TileKey, TileRecord> =
        tiles.mapNotNull { (key, staged) -> staged.candidate?.let { key to it } }.toMap()

    /**
     * Commits every changed tile once [interval] has passed since the last commit; returns how
     * many.
     */
    fun commitDue(): Int = commit(force = false)

    /** Commits every confirmed tile now. Unconfirmed views remain live but never become history. */
    fun commitAll(): Int = commit(force = true)

    private fun commit(force: Boolean): Int =
        synchronized(committing) {
            val now = clock()
            val batch = HashMap<TileKey, TileRecord>()
            val epoch: Long
            synchronized(this) {
                if (
                    !force &&
                        lastCommitAt != Long.MIN_VALUE &&
                        now - lastCommitAt < interval().toMillis().coerceAtLeast(0)
                )
                    return 0
                val due = tiles.filterValues { it.isDue() }.toList()
                if (due.isEmpty()) return 0
                // Wall-clock epochs, kept strictly increasing even if two commits share a
                // millisecond.
                epoch = maxOf(now, lastEpoch + 1)
                lastEpoch = epoch
                lastCommitAt = now
                for ((key, staged) in due) {
                    batch[key] = staged.accept(epoch)
                }
            }
            sink(Commit(epoch, batch))
            batch.size
        }

    /** Aligns the epoch sequence with a tree that already has history. */
    @Synchronized
    fun startAfter(epoch: Long) {
        lastEpoch = maxOf(lastEpoch, epoch)
    }

    companion object {
        private val directSource = Any()
    }
}
