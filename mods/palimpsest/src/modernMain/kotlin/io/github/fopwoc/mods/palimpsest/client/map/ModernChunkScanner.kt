package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
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
    private companion object {
        const val MILLIS_PER_TICK = 50L

        /** Snapshots one tick may catch up on after a stall. */
        const val MAX_VOLUME_CREDIT = 4.0
    }

    private val slice = MinimapSlice()
    private var centerSeen: TileKey? = null
    private var cursor = 0
    private var priorityCursor = 0
    private var minimapCursor = 0
    private var radiusSeen = -1
    private var heightSeen = Int.MIN_VALUE
    private var offsets = emptyList<Pair<Int, Int>>()
    private val volumes = session.history?.let(::ModernVolumes)
    private var volumeCursor = 0
    private var volumeCredit = 0.0

    override fun tick() {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        val radius = minecraft.options.renderDistance().get().coerceIn(2, 32)
        val side = radius * 2 + 1
        val pos = BlockPos.MutableBlockPos()
        val height =
            slice.ceiling(player.boundingBox.maxY, session.ceiling) { dx, dz, from ->
                val x = player.blockX + dx
                val z = player.blockZ + dz
                val chunk = level.chunkSource.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)
                if (chunk == null) null
                else
                    (from..session.ceiling).firstOrNull { y ->
                        chunk.getBlockState(pos.set(x, y, z)).blocksMotion()
                    }
            }
        session.minimap.atHeight(height)
        val centerX = player.blockX shr 4
        val centerZ = player.blockZ shr 4
        val center = TileKey(centerX, centerZ)
        val heightChanged = height != heightSeen
        if (heightChanged || radius != radiusSeen || center != centerSeen) {
            centerSeen = center
            heightSeen = height
            radiusSeen = radius
            minimapCursor = 0
            priorityCursor = 0
            offsets =
                (-radius..radius)
                    .flatMap { z -> (-radius..radius).map { x -> x to z } }
                    .sortedBy { (x, z) -> max(abs(x), abs(z)) }
        }
        val missing = session.minimap.prepare(centerX, centerZ, radius)
        val start = if (missing.isEmpty()) 0 else priorityCursor % missing.size
        val priority = (missing.drop(start) + missing.take(start)).iterator()
        val minimapBudget = ChunkScanBudget(maxOf(1, chunksPerTick / 2))
        while (minimapBudget.take()) {
            val key =
                if (priority.hasNext()) {
                    priorityCursor++
                    priority.next()
                } else {
                    val (dx, dz) = offsets[minimapCursor++ % offsets.size]
                    TileKey(centerX + dx, centerZ + dz)
                }
            val chunkX = key.x
            val chunkZ = key.z
            val chunk = level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
            if (chunk == null) {
                session.minimap.surveyed(height, key)
                continue
            }
            if (
                session.minimap.reuseVisible(height, key) { from, to ->
                    ChunkColumnsAdapter(level, chunk) { pos, state -> blockId(level, pos, state) }
                        .isAirBetween(from, to)
                }
            )
                continue
            val record = scan(level, chunk, height)
            session.minimap.observe(height, key, record)
            if (height == session.ceiling) session.map.observe(chunkX, chunkZ, record, chunk)
        }
        val surfaceBudget = ChunkScanBudget(maxOf(1, chunksPerTick / 2))
        while (surfaceBudget.take()) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            val chunk =
                level.chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
            val record = scan(level, chunk, session.ceiling)
            session.map.observe(chunkX, chunkZ, record, chunk)
            val columns =
                ChunkColumnsAdapter(level, chunk) { pos, state -> blockId(level, pos, state) }
            if (columns.isAirBetween(height + 1, session.ceiling))
                session.minimap.observe(height, TileKey(chunkX, chunkZ), record)
        }
        session.minimap.publish()
        snapshotVolumes(level, centerX, centerZ, radius)
    }

    /**
     * Stages full snapshots for history, spread so every loaded chunk is taken about once per
     * commit interval: a fraction of a chunk per tick, not a burst.
     */
    private fun snapshotVolumes(level: ClientLevel, centerX: Int, centerZ: Int, radius: Int) {
        val volumes = volumes ?: return
        val side = radius * 2 + 1
        val intervalTicks = maxOf(1L, PalimpsestConfig.commitInterval.toMillis() / MILLIS_PER_TICK)
        volumeCredit =
            minOf(volumeCredit + side * side.toDouble() / intervalTicks, MAX_VOLUME_CREDIT)
        while (volumeCredit >= 1) {
            volumeCredit--
            val index = volumeCursor++ % (side * side)
            val chunk =
                level.chunkSource.getChunk(
                    centerX - radius + index % side,
                    centerZ - radius + index / side,
                    ChunkStatus.FULL,
                    false,
                ) ?: continue
            volumes.snapshot(level, chunk)?.let { session.history?.stage(it) }
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
}
