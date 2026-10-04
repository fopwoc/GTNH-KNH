package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.world.ChunkPosition
import net.minecraft.world.chunk.Chunk

/**
 * Full snapshots of 1.7.10 chunks for history: every block as its history id. Plain blocks are
 * identified by name and dropped metadata, cached per block and metadata; tile-backed ones (GT
 * machines and ores) by what their tile entity says, read per position. Game thread.
 */
@SideOnly(Side.CLIENT)
class GtnhVolumes(private val history: DimensionHistory) {
    /**
     * (block id << 16 | meta) → history id raw, or [BY_TILE] for blocks identified per position.
     */
    private val plain = HashMap<Long, Int>()

    /**
     * The chunk as history should see it, or null while it is not complete: a chunk packet comes
     * before its tile entities', and a half-known machine stack must not become history.
     */
    fun snapshot(chunk: Chunk): ChunkObservation? {
        val world = chunk.worldObj
        val originX = chunk.xPosition shl 4
        val originZ = chunk.zPosition shl 4
        val sections = chunk.blockStorageArray
        val out = arrayOfNulls<SectionBlocks>(SECTIONS)
        for (index in 0 until SECTIONS) {
            val section = sections.getOrNull(index)?.takeUnless { it.isEmpty } ?: continue
            val blocks = IntArray(SectionBlocks.VOLUME)
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val block = section.getBlockByExtId(x, y, z)
                if (block.material === Material.air) continue
                val meta = section.getExtBlockMetadata(x, y, z)
                val key =
                    (Block.getIdFromBlock(block).toLong() shl 16) or (meta.toLong() and 0xFFFF)
                var id = plain[key] ?: identify(block, meta, key)
                if (id == BY_TILE) {
                    val wy = index * 16 + y
                    val tile = chunk.chunkTileEntityMap[ChunkPosition(x, wy, z)]
                    if (!BlockReadiness.isReady(world, originX + x, wy, originZ + z, block, tile))
                        return null
                    val look = BlockColors.of(world, originX + x, wy, originZ + z, block, meta)
                    id =
                        history
                            .idOf(identity(block, meta, look), GtnhBlockClasses.of(block, look))
                            .raw
                }
                blocks[SectionBlocks.index(x, y, z)] = id
            }
            if (blocks.any { it != 0 }) out[index] = SectionBlocks.of(blocks)
        }
        val biomes =
            IntArray(Biomes.Columns.COLUMNS) { at ->
                chunk
                    .getBiomeGenForWorldCoords(at and 15, at shr 4, world.worldChunkManager)
                    .biomeID
            }
        return ChunkObservation(
            ChunkPos(chunk.xPosition, chunk.zPosition),
            0,
            out.toList(),
            Biomes.Columns(biomes),
        )
    }

    /**
     * First meeting of a block and metadata: tile-backed, or a plain identity cached from now on.
     */
    private fun identify(block: Block, meta: Int, key: Long): Int {
        if (block.hasTileEntity(meta)) return BY_TILE.also { plain[key] = it }
        val look = BlockColors.of(block, meta)
        return history
            .idOf(identity(block, meta, look), GtnhBlockClasses.of(block, look))
            .raw
            .also { plain[key] = it }
    }

    /**
     * The map's identity of a block: its tile's look when it has one, else name and dropped
     * metadata.
     */
    private fun identity(block: Block, meta: Int, look: BlockColors.BlockColor): String {
        val name =
            Block.blockRegistry.getNameForObject(block) ?: "unknown:${Block.getIdFromBlock(block)}"
        return look.identity?.let { "$name@$it" }
            ?: "$name:${runCatching { block.damageDropped(meta) }.getOrDefault(meta)}"
    }

    private companion object {
        const val SECTIONS = 16
        const val BY_TILE = -1
    }
}
