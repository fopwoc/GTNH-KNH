package io.github.fopwoc.mods.palimpsest.client.map

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import io.github.fopwoc.mods.framework.world.ChunkColumns
import io.github.fopwoc.mods.framework.world.SliceScanner
import io.github.fopwoc.mods.framework.world.minecraft.ChunkColumnsAdapter
import net.minecraft.client.Minecraft
import net.minecraft.world.chunk.Chunk

/**
 * Walks the loaded chunks around the player a few per tick, in a fixed spiral so every chunk in
 * render distance is re-observed every couple of seconds, and hands the scans to the map. The
 * broker downstream decides what becomes history; this just looks.
 */
@SideOnly(Side.CLIENT)
class ChunkScanner(private val session: MapSession, private val chunksPerTick: Int = 8) {
    /** Heights of each scanned chunk's last row, so the chunk south of it shades continuously. */
    private val southEdges = HashMap<Long, IntArray>()
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
        val columns = ChunkColumnsAdapter(chunk, session.table, session.palette)
        val north = southEdges[key(chunk.xPosition, chunk.zPosition - 1)]
        val slice = SliceScanner.scan(columns, session.ceiling, session.palette, north)
        southEdges[key(chunk.xPosition, chunk.zPosition)] =
            slice.heights.copyOfRange(
                ChunkColumns.COLUMNS - ChunkColumns.SIDE,
                ChunkColumns.COLUMNS,
            )
        session.map.observe(
            chunk.xPosition,
            chunk.zPosition,
            IntArray(ChunkColumns.COLUMNS) { slice.colors[it].toInt() and 255 },
            slice.biomes,
        )
        if (southEdges.size > MAX_EDGES) southEdges.clear()
    }

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private companion object {
        const val MAX_EDGES = 4096
    }
}
