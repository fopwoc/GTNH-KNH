package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.engine.LocalDb
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/** A world's map storage: one folder of history plus local indexes. */
interface PalimpsestDb : AutoCloseable {
    val vocabulary: BlockVocabulary

    /** What runs in the background right now; cheap, fine to read every frame from any thread. */
    val activity: List<Activity>

    /**
     * The dimension's history, created on first use keeping full [Retention.HISTORY]. Returns at
     * once: loading, and any compaction or index rebuild, runs in the background until
     * [Dimension.ready].
     */
    fun dimension(id: DimensionId): Dimension

    /**
     * What the dimension keeps from now on, saved with the next flush. Keeping more applies at once
     * and loses nothing. Keeping less applies when the dimension is next compacted, and the history
     * it lets go is gone for good: ask the player first.
     */
    fun setRetention(id: DimensionId, retention: Retention)

    /**
     * Deletes the dimension's history and index; a [Dimension] obtained before refuses further
     * commits, and the next [dimension] call starts it empty. Completes once the files are gone.
     */
    fun drop(id: DimensionId): CompletableFuture<Unit>

    /** Makes everything committed so far durable. */
    fun flush()

    /** Waits for pending commits, seals this session's files and releases the world. Blocks. */
    override fun close()

    /** [close] on a thread of its own, for leaving a world without stalling the game. */
    fun closeAsync(): CompletableFuture<Unit>

    companion object {
        /** Storage generation this library reads and writes. */
        const val GENERATION = 3

        fun open(world: Path, config: DbConfig): OpenResult = LocalDb.open(world, config)

        /**
         * Settles a diverged world on [branch]: the other branches' files move to `abandoned/` in
         * the world folder, nothing is deleted. False when the world is open somewhere.
         */
        fun keep(world: Path, branch: OpenResult.Diverged.Branch): Boolean =
            LocalDb.keep(world, branch)
    }
}
