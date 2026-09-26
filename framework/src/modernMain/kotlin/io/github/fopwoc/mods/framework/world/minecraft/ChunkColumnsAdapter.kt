package io.github.fopwoc.mods.framework.world.minecraft

import io.github.fopwoc.mods.framework.minecraft.bottomSectionY
import io.github.fopwoc.mods.framework.minecraft.bottomY
import io.github.fopwoc.mods.framework.minecraft.topY
import io.github.fopwoc.mods.framework.world.ChunkColumns
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.QuartPos
import net.minecraft.core.SectionPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap

/**
 * Reads a loaded client chunk's sections directly instead of going through the level per block.
 * Blocks are reported through [blockId], the map's vocabulary lookup, which decides what is
 * transparent. Biomes are the surface biome of each column, identified by [BiomeTints.id]. Must be
 * used on the client thread.
 */
class ChunkColumnsAdapter(
    private val level: ClientLevel,
    private val chunk: LevelChunk,
    private val blockId: (pos: BlockPos, state: BlockState) -> Int,
) : ChunkColumns {
    private val sections = chunk.sections
    private val minSection = chunk.bottomSectionY
    private val originX = chunk.pos.x shl 4
    private val originZ = chunk.pos.z shl 4
    private val pos = BlockPos.MutableBlockPos()

    override val bottomY: Int
        get() = chunk.bottomY

    override val topY: Int
        get() = chunk.topY

    // Not a heightmap: blocks that let light through can sit above it and would never be scanned.
    // The top of the highest filled section is a safe start.
    override fun surfaceY(x: Int, z: Int): Int =
        chunk.highestFilledSectionIndex.let {
            if (it < 0) -1 else SectionPos.sectionToBlockCoord(minSection + it) + 15
        }

    override fun isSectionEmpty(section: Int): Boolean =
        sections.getOrNull(section - minSection)?.hasOnlyAir() != false

    override fun blockAt(x: Int, y: Int, z: Int): Int {
        val state = state(x, y, z) ?: return ChunkColumns.TRANSPARENT
        if (state.isAir) return ChunkColumns.TRANSPARENT
        return blockId(pos.set(originX + x, y, originZ + z), state)
    }

    override fun biomeAt(x: Int, z: Int): Int {
        val y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        return BiomeTints.id(
            chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
        )
    }

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean = state(x, y, z)?.block is LiquidBlock

    override fun isWater(x: Int, y: Int, z: Int): Boolean =
        state(x, y, z)?.let { it.block is LiquidBlock && it.fluidState.`is`(FluidTags.WATER) } ==
            true

    override fun isDecoration(x: Int, y: Int, z: Int): Boolean {
        val state = state(x, y, z) ?: return false
        return BlockColors.isDecoration(level, pos.set(originX + x, y, originZ + z), state)
    }

    private fun state(x: Int, y: Int, z: Int): BlockState? =
        sections.getOrNull((y shr 4) - minSection)?.getBlockState(x, y and 15, z)
}
