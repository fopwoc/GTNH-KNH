package io.github.fopwoc.palimpsest.db

import java.util.concurrent.CompletableFuture

/**
 * One dimension's history. Commits apply in call order; reads see a commit once its future is done.
 */
interface Dimension {
    val id: DimensionId

    /** What the dimension keeps; see [PalimpsestDb.setRetention]. */
    val retention: Retention

    /**
     * Completes once the dimension's history is loaded. Until then [latest] and [timeline] are
     * empty; reads and commits are accepted and run after it, in order.
     */
    val ready: CompletableFuture<Unit>

    /**
     * Records [observations] as the moment [tick] and returns at once; comparing, encoding and
     * writing run on the database's threads. Unchanged chunks write nothing. [tick] must be later
     * than every earlier commit's, otherwise the future fails with [TickOrderException] and the
     * commit is dropped; the dimension carries on.
     */
    fun commit(
        tick: WorldTick,
        observations: Collection<ChunkObservation>,
    ): CompletableFuture<Commit>

    /**
     * Keeps [observation] as the chunk's pending look, replacing whatever was staged for it before:
     * observe as often as you like, and only the newest look per chunk becomes history at the next
     * [commitStaged]. Cheap and safe from any thread, the game thread included.
     */
    fun stage(observation: ChunkObservation)

    /** Chunks staged and not yet committed. */
    val stagedCount: Int

    /**
     * Commits everything staged as the moment [tick]; see [commit]. With nothing staged it
     * completes at once with an empty commit and no tick is used up.
     */
    fun commitStaged(tick: WorldTick): CompletableFuture<Commit>

    val latest: Commit?

    fun timeline(): CommitTimeline

    /** The world as of [tick], which need not be a commit's exact tick. */
    fun at(tick: WorldTick): Snapshot

    /** Chunks in [window] (anywhere when null) that differ between the moments [from] and [to]. */
    fun diff(from: WorldTick, to: WorldTick, window: ChunkWindow? = null): Request<ChunkDiff>
}
