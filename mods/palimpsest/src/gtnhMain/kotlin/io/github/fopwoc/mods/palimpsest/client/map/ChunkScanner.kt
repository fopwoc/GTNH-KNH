package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.ChunkPosition
import net.minecraft.world.IBlockAccess
import net.minecraft.world.chunk.Chunk

/**
 * Scans the saved surface across the render distance and the volatile player-height section from
 * the centre outward. Section changes restart only the minimap pass, so surface coverage continues.
 */
@SideOnly(Side.CLIENT)
class ChunkScanner(private val session: MapSession, private val chunksPerTick: Int = 8) :
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
    private var heightSeen = -1
    private var offsets = emptyList<Pair<Int, Int>>()
    private val volumes = session.history?.let(::GtnhVolumes)
    private var volumeCursor = 0
    private var volumeCredit = 0.0

    override fun tick() {
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val player = minecraft.thePlayer ?: return
        val radius = minecraft.gameSettings.renderDistanceChunks.coerceIn(2, 16)
        val side = radius * 2 + 1
        val height =
            slice.ceiling(player.boundingBox.maxY, session.ceiling) { dx, dz, from ->
                val x = floor(player.posX).toInt() + dx
                val z = floor(player.posZ).toInt() + dz
                if (!world.chunkProvider.chunkExists(x shr 4, z shr 4)) null
                else {
                    val chunk = world.getChunkFromChunkCoords(x shr 4, z shr 4)
                    (from..minOf(session.ceiling, chunk.topFilledSegment + 15)).firstOrNull {
                        chunk.getBlock(x and 15, it, z and 15).material.blocksMovement()
                    }
                }
            }
        session.minimap.atHeight(height)
        val centerX = floor(player.posX).toInt() shr 4
        val centerZ = floor(player.posZ).toInt() shr 4
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
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) {
                session.minimap.surveyed(height, key)
                continue
            }
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) {
                session.minimap.surveyed(height, key)
                continue
            }
            if (
                session.minimap.reuseVisible(height, key) { from, to ->
                    ChunkColumnsAdapter(chunk, ::blockId).isAirBetween(from, to)
                }
            )
                continue
            scan(chunk, height)?.let {
                session.minimap.observe(height, key, it)
                if (height == session.ceiling) session.map.observe(chunkX, chunkZ, it, chunk)
            }
        }
        val surfaceBudget = ChunkScanBudget(maxOf(1, chunksPerTick / 2))
        while (surfaceBudget.take()) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) continue
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) continue
            scan(chunk, session.ceiling)?.let {
                session.map.observe(chunk.xPosition, chunk.zPosition, it, chunk)
                val columns = ChunkColumnsAdapter(chunk, ::blockId)
                if (columns.isAirBetween(height + 1, session.ceiling))
                    session.minimap.observe(height, TileKey(chunkX, chunkZ), it)
            }
        }
        session.minimap.publish()
        snapshotVolumes(world, centerX, centerZ, radius)
    }

    /**
     * Stages full snapshots for history, spread so every loaded chunk is taken about once per
     * commit interval: a fraction of a chunk per tick, not a burst.
     */
    private fun snapshotVolumes(
        world: net.minecraft.world.World,
        centerX: Int,
        centerZ: Int,
        radius: Int,
    ) {
        val volumes = volumes ?: return
        val side = radius * 2 + 1
        val intervalTicks = maxOf(1L, PalimpsestConfig.commitInterval.toMillis() / MILLIS_PER_TICK)
        volumeCredit =
            minOf(volumeCredit + side * side.toDouble() / intervalTicks, MAX_VOLUME_CREDIT)
        while (volumeCredit >= 1) {
            volumeCredit--
            val index = volumeCursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) continue
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) continue
            volumes.snapshot(chunk)?.let(session.history!!::stage)
        }
    }

    /** Nothing is buffered here; the map's broker holds pending observations. */
    override fun flush() = Unit

    private fun scan(chunk: Chunk, ceiling: Int): TileRecord? {
        var complete = true
        val columns =
            ChunkColumnsAdapter(chunk) { world, x, y, z, block, meta ->
                val tile = chunk.chunkTileEntityMap[ChunkPosition(x and 15, y, z and 15)]
                if (!BlockReadiness.isReady(world, x, y, z, block, tile)) {
                    complete = false
                    session.blocks.nothing
                } else {
                    blockId(world, x, y, z, block, meta)
                }
            }
        val scan = TileScanner.scan(columns, ceiling)
        // A chunk packet precedes its tile-entity packets. Publishing the partial scan would make
        // tall GT machine stacks briefly collapse to a lower machine and become map history.
        if (!complete) return null
        return TileRecord.build(
            0,
            scan.block::get,
            scan.height::get,
            scan.depth::get,
            scan.biome::get,
        )
    }

    /**
     * The vocabulary id of stable block structure at this position, recorded with its current
     * colour the first time it is seen. Generic blocks use their dropped metadata, which normally
     * preserves material variants while removing runtime and placement bits. Providers can supply a
     * stable identity for tile-backed blocks such as GregTech machines.
     */
    private fun blockId(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): Int {
        val name = Block.blockRegistry.getNameForObject(block) ?: return session.blocks.nothing
        val color = BlockColors.of(world, x, y, z, block, meta)
        if (color.isTransparent) return session.blocks.nothing
        val identity = color.identity
        val key =
            if (identity != null) "$name@$identity"
            else "$name:${runCatching { block.damageDropped(meta) }.getOrDefault(meta)}"
        val known = session.blocks.idOf(key)
        if (known != 0) return known
        return session.blocks.idOf(key, color.argb and 0xFFFFFF, color.tint.ordinal)
    }
}
