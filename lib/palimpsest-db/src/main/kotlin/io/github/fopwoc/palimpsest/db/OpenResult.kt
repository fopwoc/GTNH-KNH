package io.github.fopwoc.palimpsest.db

/** How opening a world folder went; every case is an expected outcome the mod must handle. */
sealed interface OpenResult {
    class Opened(val db: PalimpsestDb) : OpenResult

    /** This or another game already has the world open. */
    class Locked(val holder: String) : OpenResult

    /** The manifest names files the cloud has not delivered yet, or not completely. */
    class SyncIncomplete(val missing: List<String>) : OpenResult

    /** Two computers continued the same history separately. */
    class Diverged(val sessions: List<String>) : OpenResult

    /** A map of another storage generation; the mod archives it. */
    class Incompatible(val generation: Int) : OpenResult
}
