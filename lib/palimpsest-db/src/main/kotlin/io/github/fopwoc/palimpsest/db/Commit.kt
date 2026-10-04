package io.github.fopwoc.palimpsest.db

/**
 * One moment of history: its [tick], the wall-clock label [observedAt] (epoch millis, for dates in
 * the UI only) and what it cost.
 */
data class Commit(
    val tick: WorldTick,
    val observedAt: Long,
    val chunksChanged: Int,
    val sectionsWritten: Int,
    val bytes: Long,
)
