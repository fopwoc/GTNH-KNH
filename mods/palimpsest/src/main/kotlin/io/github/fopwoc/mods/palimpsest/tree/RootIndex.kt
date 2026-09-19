package io.github.fopwoc.mods.palimpsest.tree

/**
 * The time index: every committed root by epoch. Immutable snapshot swapped on each commit, so
 * readers never lock; `rootAt(epoch)` is a binary search.
 */
class RootIndex private constructor(private val epochs: LongArray, private val roots: Array<RootRecord>) {
    constructor(roots: List<RootRecord>) : this(roots.sortedBy { it.epoch }.map { it.epoch }.toLongArray(), roots.sortedBy { it.epoch }.toTypedArray())

    val size: Int
        get() = epochs.size

    val latestEpoch: Long
        get() = if (epochs.isEmpty()) -1 else epochs.last()

    val latest: RootRecord
        get() = if (roots.isEmpty()) RootRecord.EMPTY else roots.last()

    /** The root in force at [epoch]: the newest one committed at or before it; empty before the first. */
    fun rootAt(epoch: Long): RootRecord {
        var low = 0
        var high = epochs.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (epochs[middle] <= epoch) {
                found = middle
                low = middle + 1
            } else high = middle - 1
        }
        return if (found < 0) RootRecord.EMPTY else roots[found]
    }

    fun with(root: RootRecord): RootIndex {
        require(root.epoch > latestEpoch) { "Epoch ${root.epoch} not after $latestEpoch" }
        return RootIndex(epochs + root.epoch, roots + root)
    }

    fun epochs(): LongArray = epochs.copyOf()
}
