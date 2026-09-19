package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.world.BlockColorTable
import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.WorldPalette
import net.minecraft.block.Block
import net.minecraft.world.chunk.Chunk

/**
 * Reads a loaded chunk's section arrays directly (block LSB bytes, optional MSB and metadata
 * nibbles) instead of going through `world.getBlock` per block, which would re-resolve the chunk
 * every time. Entries come from the map's [BlockColorTable]; a block the map has never seen is
 * snapped from its current texture ([BlockColors]) into its band of the frozen [WorldPalette] and
 * recorded. Must be used on the thread that owns the chunk (the client thread).
 */
class ChunkColumnsAdapter(
    private val chunk: Chunk,
    private val table: BlockColorTable,
    private val palette: WorldPalette,
) : ChunkColumns {
    private val sections = chunk.blockStorageArray

    override val topY: Int
        get() = sections.size * ChunkColumns.SIDE - 1

    override fun surfaceY(x: Int, z: Int): Int = chunk.getHeightValue(x, z) - 1

    override fun isSectionEmpty(section: Int): Boolean =
        section !in sections.indices || sections[section]?.isEmpty != false

    override fun entryAt(x: Int, y: Int, z: Int): Int {
        val section = sections[y shr 4] ?: return ChunkColumns.TRANSPARENT
        val local = y and 15
        val block = section.getBlockByExtId(x, local, z)
        val meta = section.getExtBlockMetadata(x, local, z)
        return table.entryOf(Block.blockRegistry.getNameForObject(block), meta) {
            val color = BlockColors.of(block, meta)
            palette.nearestFor(color.argb, color.tintable)
        }
    }

    override fun biomeAt(x: Int, z: Int): Int = chunk.biomeArray[z shl 4 or x].toInt() and 255

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean {
        val section = sections[y shr 4] ?: return false
        return section.getBlockByExtId(x, y and 15, z).material.isLiquid
    }
}
