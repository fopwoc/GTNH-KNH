package io.github.fopwoc.mods.palimpsest.tree

/**
 * The time index: every committed root by epoch. Immutable snapshot swapped on each commit, so
 * readers never lock; `rootAt(epoch)` is a binary search.
 */
class RootIndex private constructor(private val epochs: LongArray, private val refs: LongArray) {
    constructor(roots: List<Pair<Long, Ref>>) :
        this(
            roots.sortedBy { it.first }.map { it.first }.toLongArray(),
            roots.sortedBy { it.first }.map { it.second.packed }.toLongArray(),
        )

    val size: Int
        get() = epochs.size

    val latestEpoch: Long
        get() = if (epochs.isEmpty()) -1 else epochs.last()

    val latest: Ref
        get() = if (refs.isEmpty()) Ref.NULL else Ref(refs.last())

    /** The root in force at [epoch]: the newest one committed at or before it; null before the first. */
    fun rootAt(epoch: Long): Ref {
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
        return if (found < 0) Ref.NULL else Ref(refs[found])
    }

    /** The epoch of the root in force at [epoch], or -1. */
    fun epochAt(epoch: Long): Long {
        val ref = rootAt(epoch)
        if (ref.isNull) return -1
        return epochs[refs.indexOfLast { it == ref.packed }]
    }

    fun with(epoch: Long, root: Ref): RootIndex {
        require(epoch > latestEpoch) { "Epoch $epoch not after $latestEpoch" }
        return RootIndex(epochs + epoch, refs + root.packed)
    }

    fun epochs(): LongArray = epochs.copyOf()
}
