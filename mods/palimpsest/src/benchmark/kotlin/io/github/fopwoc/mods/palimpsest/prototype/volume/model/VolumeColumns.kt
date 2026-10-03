package io.github.fopwoc.mods.palimpsest.prototype.volume.model

import io.github.fopwoc.mods.framework.world.ChunkColumns

/**
 * A stored volume seen through the production scanner's interface, so a view from any ceiling is
 * exactly `TileScanner.scan(VolumeColumns(volume, kinds), ceiling)`.
 */
class VolumeColumns(private val volume: ChunkVolume, private val kinds: Array<BlockKind>) :
    ChunkColumns {
    override val topY: Int = ChunkVolume.HEIGHT - 1

    private val surface = IntArray(ChunkColumns.COLUMNS) { at -> topOf(at % 16, at / 16) }

    private fun topOf(x: Int, z: Int): Int {
        for (section in ChunkVolume.SECTIONS - 1 downTo 0) {
            val blocks = volume.section(section) ?: continue
            for (y in 15 downTo 0)
                if (blocks[ChunkVolume.index(x, y, z)] != 0) return section * 16 + y
        }
        return -1
    }

    override fun surfaceY(x: Int, z: Int): Int = surface[z * 16 + x]

    override fun isSectionEmpty(section: Int): Boolean = volume.section(section) == null

    override fun blockAt(x: Int, y: Int, z: Int): Int {
        val id = volume.block(x, y, z)
        return when (kinds[id]) {
            BlockKind.AIR, BlockKind.TRANSPARENT -> ChunkColumns.TRANSPARENT
            else -> id
        }
    }

    override fun isLiquid(x: Int, y: Int, z: Int): Boolean =
        kinds[volume.block(x, y, z)].let { it == BlockKind.WATER || it == BlockKind.LIQUID }

    override fun isWater(x: Int, y: Int, z: Int): Boolean =
        kinds[volume.block(x, y, z)] == BlockKind.WATER

    override fun isDecoration(x: Int, y: Int, z: Int): Boolean =
        kinds[volume.block(x, y, z)] == BlockKind.DECORATION

    override fun biomeAt(x: Int, z: Int): Int = volume.biome(x, z)
}
