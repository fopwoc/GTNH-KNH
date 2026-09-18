package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.world.BlockColorTable
import io.github.fopwoc.mods.framework.world.ChunkColumns
import net.minecraft.block.Block
import net.minecraft.world.chunk.Chunk

/**
 * Reads a loaded chunk's section arrays directly (block LSB bytes, optional MSB and metadata
 * nibbles) instead of going through `world.getBlock` per block, which would re-resolve the chunk
 * every time. Colors come from the map's [BlockColorTable]; a block the map has never seen is
 * recorded from the current textures via [BlockColors]. Must be used on the thread that owns the
 * chunk (the client thread).
 */
class ChunkColumnsAdapter(private val chunk: Chunk, private val table: BlockColorTable) :
    ChunkColumns {
    private val sections = chunk.blockStorageArray

    override val topY: Int
        get() = sections.size * ChunkColumns.SIDE - 1

    override fun surfaceY(x: Int, z: Int): Int = chunk.getHeightValue(x, z) - 1

    override fun isSectionEmpty(section: Int): Boolean =
        section !in sections.indices || sections[section]?.isEmpty != false

    override fun colorAt(x: Int, y: Int, z: Int): Int {
        val section = sections[y shr 4] ?: return ChunkColumns.TRANSPARENT
        val local = y and 15
        val block = section.getBlockByExtId(x, local, z)
        val meta = section.getExtBlockMetadata(x, local, z)
        return table.colorOf(Block.blockRegistry.getNameForObject(block), meta) {
            BlockColors.of(block, meta)
        }
    }

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean {
        val section = sections[y shr 4] ?: return false
        return section.getBlockByExtId(x, y and 15, z).material.isLiquid
    }
}
