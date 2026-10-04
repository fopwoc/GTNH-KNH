package io.github.fopwoc.palimpsest.db.engine

import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.BlockId
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.ChunkVolume
import io.github.fopwoc.palimpsest.db.SectionBlocks

internal class DecodedVolume(
    override val pos: ChunkPos,
    minSection: Int,
    private val sections: Array<IntArray?>,
    override val biomes: Biomes,
) : ChunkVolume {
    override val minY: Int = minSection * SectionBlocks.SIDE
    override val maxY: Int = minY + sections.size * SectionBlocks.SIDE

    override fun block(x: Int, y: Int, z: Int): BlockId {
        if (y !in minY until maxY) return BlockId.AIR
        val section = sections[(y - minY) shr 4] ?: return BlockId.AIR
        return BlockId(section[SectionBlocks.index(x, (y - minY) and 15, z)])
    }
}
