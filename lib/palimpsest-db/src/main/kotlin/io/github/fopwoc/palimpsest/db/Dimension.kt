package io.github.fopwoc.palimpsest.db

import java.util.concurrent.CompletableFuture

/**
 * One dimension's history. Commits apply in call order; reads see a commit once its future is done.
 */
interface Dimension {
    val id: DimensionId

    val mode: DimensionMode

    /**
     * Records [observations] as the moment [tick] and returns at once; comparing, encoding and
     * writing run on the database's threads. Unchanged chunks write nothing. [tick] must be later
     * than every earlier commit's, otherwise the future fails with [TickOrderException].
     */
    fun commit(
        tick: WorldTick,
        observations: Collection<ChunkObservation>,
    ): CompletableFuture<Commit>

    val latest: Commit?

    fun timeline(): CommitTimeline

    /** The world as of [tick], which need not be a commit's exact tick. */
    fun at(tick: WorldTick): Snapshot
}
