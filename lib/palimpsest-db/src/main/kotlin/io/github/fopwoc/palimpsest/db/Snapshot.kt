package io.github.fopwoc.palimpsest.db

/** The world at one moment. It and everything it returns are immutable and thread-safe. */
interface Snapshot {
    /** The last commit at or before the requested tick; null before the first one. */
    val commit: Commit?

    /** The full 3D chunk, decoded from history: the cold path. Null if never seen by then. */
    fun volume(chunk: ChunkPos): Request<ChunkVolume?>
}
