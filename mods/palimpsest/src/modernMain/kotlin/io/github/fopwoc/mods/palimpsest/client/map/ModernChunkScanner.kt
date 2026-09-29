package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.math.abs
import kotlin.math.max
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

/**
 * Scans the saved surface across the render distance and the volatile player-height section from
 * the centre outward. Section changes restart only the minimap pass, so surface coverage continues.
 */
class ModernChunkScanner(private val session: MapSession, private val chunksPerTick: Int = 8) :
    MapScanner {
    private var cursor = 0
    private var minimapCursor = 0
    private var radiusSeen = -1
    private var heightSeen = Int.MIN_VALUE
    private var offsets = emptyList<Pair<Int, Int>>()

    override fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        val radius = minecraft.options.renderDistance().get().coerceIn(2, 32)
        val side = radius * 2 + 1
        val height = MinimapSlice.ceiling(player.blockY)
        val cachedHeight = session.minimap.atHeight(height)
        val heightChanged = height != heightSeen
        if (heightChanged || radius != radiusSeen) {
            heightSeen = height
            radiusSeen = radius
            minimapCursor = 0
            offsets =
                (-radius..radius)
                    .flatMap { z -> (-radius..radius).map { x -> x to z } }
                    .sortedBy { (x, z) -> max(abs(x), abs(z)) }
        }
        val centerX = player.blockX shr 4
        val centerZ = player.blockZ shr 4
        repeat(chunksPerTick) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            val chunk =
                level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return@repeat
            val record = scan(level, chunk, session.ceiling)
            session.map.observe(chunkX, chunkZ, record, chunk)
        }
        repeat(if (heightChanged && !cachedHeight) HEIGHT_WARMUP_CHUNKS else chunksPerTick) {
            val (dx, dz) = offsets[minimapCursor++ % offsets.size]
            val chunkX = centerX + dx
            val chunkZ = centerZ + dz
            val chunk =
                level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return@repeat
            val key = TileKey(chunkX, chunkZ)
            if (
                session.minimap.reuseVisible(height, key) {
                    ChunkColumnsAdapter(level, chunk) { pos, state -> blockId(level, pos, state) }
                        .surfaceY(0, 0)
                }
            )
                return@repeat
            val record = scan(level, chunk, height)
            session.minimap.observe(height, key, record)
        }
    }

    /** Nothing is buffered here; the map's broker holds pending observations. */
    override fun flush() = Unit

    private fun scan(level: ClientLevel, chunk: LevelChunk, ceiling: Int): TileRecord {
        val columns = ChunkColumnsAdapter(level, chunk) { pos, state -> blockId(level, pos, state) }
        val scan = TileScanner.scan(columns, ceiling.coerceIn(columns.bottomY, columns.topY))
        return TileRecord.build(
            0,
            scan.block::get,
            scan.height::get,
            scan.depth::get,
            scan.biome::get,
        )
    }

    /**
     * The vocabulary id of the block, recorded with its current colour the first time it is seen.
     * Modern Minecraft gives every material variant its own block, so the name alone is the key.
     */
    private fun blockId(level: ClientLevel, pos: BlockPos, state: BlockState): Int {
        val color = BlockColors.of(level, pos, state)
        if (color.isTransparent) return session.blocks.nothing
        val key = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        val known = session.blocks.idOf(key)
        if (known != 0) return known
        return session.blocks.idOf(key, color.argb and 0xFFFFFF, color.tint.ordinal)
    }

    private companion object {
        const val HEIGHT_WARMUP_CHUNKS = 24
    }
}
