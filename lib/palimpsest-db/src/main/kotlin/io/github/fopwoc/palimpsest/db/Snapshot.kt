package io.github.fopwoc.palimpsest.db

/** The world at one moment. It and everything it returns are immutable and thread-safe. */
interface Snapshot {
    /** The last commit at or before the requested tick; null before the first one. */
    val commit: Commit?

    /** The window seen from above, from the index's surfaces: the map's everyday read. */
    fun surface(window: ChunkWindow): Request<SurfaceGrid>

    /**
     * The window seen from [y] downward, for caves and rooms: what a player under a roof at [y]
     * would want on the map. Columns read as in [surface]. A surface-only dimension stores nothing
     * under the roof and answers with its surface.
     */
    fun ceiling(window: ChunkWindow, y: Int): Request<SurfaceGrid>

    /**
     * Far zoom: one sample per 2^[level] × 2^[level] chunks covering [window], picked, not
     * averaged. Reads small per-region sample histories, so a whole world fits one view.
     */
    fun overview(window: ChunkWindow, level: Int): Request<SampleGrid>

    /**
     * The full 3D chunk, decoded from history: the cold path. Null if never seen by then, and
     * always in a surface-only dimension.
     */
    fun volume(chunk: ChunkPos): Request<ChunkVolume?>
}
