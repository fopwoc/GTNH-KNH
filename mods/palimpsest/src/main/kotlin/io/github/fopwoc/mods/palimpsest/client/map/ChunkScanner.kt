package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.TileScanner
import io.github.fopwoc.mods.framework.world.minecraft.BlockColors
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import net.minecraft.block.Block
import net.minecraft.world.IBlockAccess
import net.minecraft.client.Minecraft
import net.minecraft.world.chunk.Chunk

/**
 * Walks the loaded chunks around the player a few per tick, in a fixed spiral so every chunk in
 * render distance is re-observed every couple of seconds, and hands the scans to the map. The
 * broker downstream decides what becomes history; this just looks.
 */
@SideOnly(Side.CLIENT)
class ChunkScanner(private val session: MapSession, private val chunksPerTick: Int = 8) {
    private var cursor = 0

    fun tick() {
        val minecraft = Minecraft.getMinecraft()
        val world = minecraft.theWorld ?: return
        val player = minecraft.thePlayer ?: return
        val radius = minecraft.gameSettings.renderDistanceChunks.coerceIn(2, 16)
        val side = radius * 2 + 1
        val centerX = player.posX.toInt() shr 4
        val centerZ = player.posZ.toInt() shr 4
        repeat(chunksPerTick) {
            val index = cursor++ % (side * side)
            val chunkX = centerX - radius + index % side
            val chunkZ = centerZ - radius + index / side
            if (!world.chunkProvider.chunkExists(chunkX, chunkZ)) return@repeat
            val chunk = world.getChunkFromChunkCoords(chunkX, chunkZ)
            if (chunk.isEmpty) return@repeat
            observe(chunk)
        }
    }

    /** Nothing is buffered here; the map's broker holds pending observations. */
    fun flush() = Unit

    private fun observe(chunk: Chunk) {
        val columns = ChunkColumnsAdapter(chunk, ::blockId)
        val scan = TileScanner.scan(columns, session.ceiling)
        val record =
            TileRecord.build(0, scan.block::get, scan.height::get, scan.depth::get, scan.biome::get)
        session.map.observe(chunk.xPosition, chunk.zPosition, record)
    }

    /**
     * The vocabulary id of a block as it shows at this position, recorded with its current colour
     * the first time it is seen. Blocks whose texture comes from a tile entity (GregTech machines)
     * get one entry per texture: `mod:block:meta@icon`.
     */
    private fun blockId(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, meta: Int): Int {
        val name = Block.blockRegistry.getNameForObject(block) ?: return session.blocks.nothing
        val color = BlockColors.of(world, x, y, z, block, meta)
        if (color.isTransparent) return session.blocks.nothing
        val key = if (color.variant == null) "$name:$meta" else "$name:$meta@${color.variant}"
        val known = session.blocks.idOf(key)
        if (known != 0) return known
        return session.blocks.idOf(key, color.argb and 0xFFFFFF, color.tintable)
    }
}
