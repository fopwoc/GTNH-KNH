package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
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
    private var cursor = 0
    private var minimapCursor = 0
    private var radiusSeen = -1
    private var heightSeen = -1
    private var offsets = emptyList<Pair<Int, Int>>()

    override fun tick() {
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val player = minecraft.thePlayer ?: return
        val radius = minecraft.gameSettings.renderDistanceChunks.coerceIn(2, 16)
        val side = radius * 2 + 1
        val height = MinimapSlice.ceiling(floor(player.posY).toInt()).coerceIn(0, session.ceiling)
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
        val centerX = floor(player.posX).toInt() shr 4
        val centerZ = floor(player.posZ).toInt() shr 4
        repeat(chunksPerTick) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) return@repeat
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) return@repeat
            scan(chunk, session.ceiling)?.let {
                session.map.observe(chunk.xPosition, chunk.zPosition, it, chunk)
            }
        }
        repeat(if (heightChanged && !cachedHeight) HEIGHT_WARMUP_CHUNKS else chunksPerTick) {
            val (dx, dz) = offsets[minimapCursor++ % offsets.size]
            val chunkX = centerX + dx
            val chunkZ = centerZ + dz
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) return@repeat
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) return@repeat
            scan(chunk, height)?.let {
                session.minimap.observe(height, TileKey(chunkX, chunkZ), it)
            }
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

    private companion object {
        const val HEIGHT_WARMUP_CHUNKS = 24
    }
}
