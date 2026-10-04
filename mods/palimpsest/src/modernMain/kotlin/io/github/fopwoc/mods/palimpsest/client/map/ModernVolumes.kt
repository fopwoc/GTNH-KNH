package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.minecraft.bottomSectionY
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.palimpsest.db.Biomes
import io.github.fopwoc.palimpsest.db.ChunkObservation
import io.github.fopwoc.palimpsest.db.ChunkPos
import io.github.fopwoc.palimpsest.db.SectionBlocks
import java.util.IdentityHashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Full snapshots of modern chunks for history: every block state as the history id of its block,
 * cached per state. Modern Minecraft gives every material variant its own block, so the registry
 * key is the identity. Game thread.
 */
class ModernVolumes(private val history: DimensionHistory) {
    private val byState = IdentityHashMap<BlockState, Int>()
    private val pos = BlockPos.MutableBlockPos()

    /** The chunk as history should see it, or null when it is taller than history can hold. */
    fun snapshot(level: ClientLevel, chunk: LevelChunk): ChunkObservation? {
        if (chunk.sections.size > ChunkObservation.MAX_SECTIONS) return null
        val minSection = chunk.bottomSectionY
        val originX = chunk.pos.x shl 4
        val originZ = chunk.pos.z shl 4
        val sections =
            chunk.sections.mapIndexed { index, section ->
                if (section.hasOnlyAir()) return@mapIndexed null
                val blocks = IntArray(SectionBlocks.VOLUME)
                for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                    val state = section.getBlockState(x, y, z)
                    if (state.isAir) continue
                    blocks[SectionBlocks.index(x, y, z)] =
                        byState.getOrPut(state) {
                            pos.set(originX + x, (minSection + index) * 16 + y, originZ + z)
                            val look = BlockColors.of(level, pos, state)
                            history
                                .idOf(
                                    BuiltInRegistries.BLOCK.getKey(state.block).toString(),
                                    ModernBlockClasses.of(state, look),
                                )
                                .raw
                        }
                }
                if (blocks.any { it != 0 }) SectionBlocks.of(blocks) else null
            }
        val columns = ChunkColumnsAdapter(level, chunk) { _, _ -> 0 }
        val biomes = IntArray(Biomes.Columns.COLUMNS) { at -> columns.biomeAt(at and 15, at shr 4) }
        return ChunkObservation(
            ChunkPos(chunk.pos.x, chunk.pos.z),
            minSection,
            sections,
            Biomes.Columns(biomes),
        )
    }
}
