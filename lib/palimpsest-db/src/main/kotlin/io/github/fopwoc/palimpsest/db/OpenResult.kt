package io.github.fopwoc.palimpsest.db

/** How opening a world folder went; every case is an expected outcome the mod must handle. */
sealed interface OpenResult {
    class Opened(val db: PalimpsestDb) : OpenResult

    /** This or another game already has the world open. */
    class Locked(val holder: String) : OpenResult

    /** The manifest names files the cloud has not delivered yet, or not completely. */
    class SyncIncomplete(val missing: List<String>) : OpenResult

    /**
     * Two or more computers continued the same history apart. Nothing is merged: the player picks a
     * branch and [PalimpsestDb.keep] sets the others aside.
     */
    class Diverged(val branches: List<Branch>) : OpenResult {
        /** One line of history: the session that last wrote it and when, in epoch millis. */
        class Branch internal constructor(val session: String, val writtenAt: Long)
    }

    /** A map of another storage generation; the mod archives it. */
    class Incompatible(val generation: Int) : OpenResult
}
