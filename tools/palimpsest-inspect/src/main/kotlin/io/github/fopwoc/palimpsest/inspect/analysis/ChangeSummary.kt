package io.github.fopwoc.palimpsest.inspect.analysis

import io.github.fopwoc.palimpsest.db.ChunkPos

/** What a dimension's commits recorded as changed, sorted into kinds. Identities are names. */
class ChangeSummary(
    val commits: Int,
    /** Chunk changes between neighbouring commits, the chunk's first appearance included. */
    val changes: Int,
    val appeared: Int,
    /** Changes whose versions hold the same blocks and biomes: nothing changed at all. */
    val identical: Int,
    val biomesOnly: Int,
    /** Changes the database marks as visible from above. */
    val surfaceChanges: Int,
    val chunksPerCommit: List<Int>,
    val sectionsEmptied: Int,
    val sectionsFilled: Int,
    /** From → to identity → blocks. */
    val blockPairs: Map<Pair<String, String>, Int>,
    /** From → to identity of the top block → columns, over surface changes. */
    val surfacePairs: Map<Pair<String, String>, Int>,
    val lostBlocks: Long,
    val undoneLosses: List<UndoneLoss>,
) {
    /**
     * Pairs that keep the block and change only what follows its name: metadata, or a tile's
     * details such as a GregTech machine's id, colour and facing.
     */
    val sameBlockPairs: Map<Pair<String, String>, Int>
        get() = blockPairs.filterKeys { (from, to) ->
            block(from) != null && block(from) == block(to)
        }

    /**
     * Blocks only taken away over one or more commits, then back exactly as they were: a chunk seen
     * half loaded or half unloaded, a region moved away and back, or a restored backup.
     */
    class UndoneLoss(
        val pos: ChunkPos,
        val blocks: Int,
        val lostAt: Long,
        val lostObservedAt: Long,
        val backAt: Long,
    )

    companion object {
        /** The block an identity names, without metadata or tile details; null for air. */
        fun block(identity: String): String? =
            if (identity == AIR) null else identity.substringBefore('@').substringBeforeLast(':')

        const val AIR = "minecraft:air"
    }
}
