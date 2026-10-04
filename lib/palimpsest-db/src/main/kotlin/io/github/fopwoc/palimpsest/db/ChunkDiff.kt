package io.github.fopwoc.palimpsest.db

/**
 * What changed between two moments: every chunk whose version at [to] differs from the one at
 * [from], in no particular order. Playing history back is a diff between neighbouring commits plus
 * re-reading only these chunks.
 */
class ChunkDiff
internal constructor(val from: WorldTick, val to: WorldTick, val changes: List<Change>) {
    /**
     * One chunk's change. [sections] has bit i set when section i (counted up from the chunk's
     * lowest section) differs; [appeared] means the chunk did not exist yet at [from]; [surface]
     * means the change shows from above.
     */
    class Change(
        val pos: ChunkPos,
        val sections: Long,
        val biomes: Boolean,
        val surface: Boolean,
        val appeared: Boolean,
    )
}
