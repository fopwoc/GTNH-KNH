package io.github.fopwoc.palimpsest.db

/** A dimension's commits in tick order, as of the moment it was taken. */
class CommitTimeline internal constructor(private val commits: List<Commit>) :
    List<Commit> by commits {
    /** The last commit at or before [tick]: what a snapshot at [tick] shows. */
    fun atOrBefore(tick: WorldTick): Commit? = commits.getOrNull(search(tick))

    fun next(tick: WorldTick): Commit? = commits.getOrNull(search(tick) + 1)

    fun previous(tick: WorldTick): Commit? {
        val at = search(tick)
        return commits.getOrNull(if (commits.getOrNull(at)?.tick == tick) at - 1 else at)
    }

    /** Index of the last commit with tick ≤ [tick], or -1. */
    private fun search(tick: WorldTick): Int {
        val found = commits.binarySearch { it.tick.compareTo(tick) }
        return if (found >= 0) found else -found - 2
    }
}
