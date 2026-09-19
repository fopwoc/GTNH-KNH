package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.world.ChunkColumns
import net.minecraft.block.Block
import net.minecraft.world.chunk.Chunk

/**
 * Reads a loaded chunk's section arrays directly (block LSB bytes, optional MSB and metadata
 * nibbles) instead of going through `world.getBlock` per block, which would re-resolve the chunk
 * every time. Blocks are reported through [blockId], the map's vocabulary lookup, which decides
 * what is transparent. Must be used on the thread that owns the chunk (the client thread).
 */
class ChunkColumnsAdapter(private val chunk: Chunk, private val blockId: (Block, Int) -> Int) : ChunkColumns {
    private val sections = chunk.blockStorageArray

    override val topY: Int
        get() = sections.size * ChunkColumns.SIDE - 1

    override fun surfaceY(x: Int, z: Int): Int = chunk.getHeightValue(x, z) - 1

    override fun isSectionEmpty(section: Int): Boolean =
        section !in sections.indices || sections[section]?.isEmpty != false

    override fun blockAt(x: Int, y: Int, z: Int): Int {
        val section = sections[y shr 4] ?: return ChunkColumns.TRANSPARENT
        val local = y and 15
        return blockId(section.getBlockByExtId(x, local, z), section.getExtBlockMetadata(x, local, z))
    }

    // Not `chunk.biomeArray`: EndlessIDs keeps biomes in a short array and throws on the vanilla
    // byte array, while this lookup is the one it overrides to read its own storage.
    override fun biomeAt(x: Int, z: Int): Int =
        chunk.getBiomeGenForWorldCoords(x, z, chunk.worldObj.worldChunkManager).biomeID

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean {
        val section = sections[y shr 4] ?: return false
        return section.getBlockByExtId(x, y and 15, z).material.isLiquid
    }
}
