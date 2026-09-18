package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.world.ChunkColumns
import net.minecraft.block.Block
import net.minecraft.block.material.MapColor
import net.minecraft.world.chunk.Chunk

/**
 * Reads a loaded chunk's section arrays directly (block LSB bytes, optional MSB and metadata
 * nibbles) instead of going through `world.getBlock` per block, which would re-resolve the chunk
 * every time. Must be used on the thread that owns the chunk (the client thread).
 */
class ChunkColumnsAdapter(private val chunk: Chunk) : ChunkColumns {
    private val sections = chunk.blockStorageArray

    override val topY: Int
        get() = sections.size * ChunkColumns.SIDE - 1

    override fun surfaceY(x: Int, z: Int): Int = chunk.getHeightValue(x, z) - 1

    override fun isSectionEmpty(section: Int): Boolean =
        section !in sections.indices || sections[section]?.isEmpty != false

    override fun colorIndex(x: Int, y: Int, z: Int): Int {
        val section = sections[y shr 4] ?: return ChunkColumns.TRANSPARENT
        val local = y and 15
        val block = section.getBlockByExtId(x, local, z)
        val color: MapColor = block.getMapColor(section.getExtBlockMetadata(x, local, z))
        return color.colorIndex
    }

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean {
        val section = sections[y shr 4] ?: return false
        val block: Block = section.getBlockByExtId(x, y and 15, z)
        return block.material.isLiquid
    }
}
