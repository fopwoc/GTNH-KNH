package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

/**
 * Walks the loaded chunks around the player a few per tick, in a fixed spiral so every chunk in
 * render distance is re-observed every couple of seconds, and hands the scans to the map. The
 * broker downstream decides what becomes history; this just looks.
 */
class ModernChunkScanner(private val session: MapSession, private val chunksPerTick: Int = 8) :
    MapScanner {
    private var cursor = 0

    override fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        val radius = minecraft.options.renderDistance().get().coerceIn(2, 32)
        val side = radius * 2 + 1
        val centerX = player.blockX shr 4
        val centerZ = player.blockZ shr 4
        repeat(chunksPerTick) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            val chunk =
                level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: return@repeat
            observe(level, chunk)
        }
    }

    /** Nothing is buffered here; the map's broker holds pending observations. */
    override fun flush() = Unit

    private fun observe(level: ClientLevel, chunk: LevelChunk) {
        val columns = ChunkColumnsAdapter(level, chunk) { pos, state -> blockId(level, pos, state) }
        val scan = TileScanner.scan(columns, session.ceiling.coerceAtMost(columns.topY))
        val record =
            TileRecord.build(0, scan.block::get, scan.height::get, scan.depth::get, scan.biome::get)
        session.map.observe(chunk.pos.x, chunk.pos.z, record, chunk)
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
}
