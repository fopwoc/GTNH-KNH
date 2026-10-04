package io.github.fopwoc.palimpsest.db

import io.github.fopwoc.palimpsest.db.engine.LocalDb
import java.nio.file.Path

/** A world's map storage: one folder of history plus local indexes. */
interface PalimpsestDb : AutoCloseable {
    val vocabulary: BlockVocabulary

    /** What runs in the background right now; cheap, fine to read every frame from any thread. */
    val activity: List<Activity>

    /**
     * The dimension's history, created with [mode] on first use. Returns at once: loading, and any
     * compaction or index rebuild, runs in the background until [Dimension.ready].
     */
    fun dimension(id: DimensionId, mode: DimensionMode): Dimension

    /** Makes everything committed so far durable. */
    fun flush()

    /** Waits for pending commits, seals this session's files and releases the world. */
    override fun close()

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
