package io.github.fopwoc.mods.framework.world

/**
 * Read access to one chunk's blocks for top-down scanning, independent of Minecraft classes.
 *
 * Coordinates are chunk-local (0..15) and world Y. Blocks are reported as [WorldPalette] entries,
 * which carry both the color and whether the biome tints it; [TRANSPARENT] means the map looks
 * through the block: air, glass, torches, tall grass.
 */
interface ChunkColumns {
    /** Highest block Y the chunk can hold. */
    val topY: Int

    /** Highest Y that blocks sky light in the column, or -1 if the column is entirely open. */
    fun surfaceY(x: Int, z: Int): Int

    /** True when no block in the 16-block band starting at `section * 16` exists. */
    fun isSectionEmpty(section: Int): Boolean

    fun entryAt(x: Int, y: Int, z: Int): Int

    fun isLiquid(x: Int, y: Int, z: Int): Boolean

    /** Biome id of the column, 0..255. */
    fun biomeAt(x: Int, z: Int): Int

    companion object {
        const val SIDE = 16
        const val COLUMNS = SIDE * SIDE
        const val TRANSPARENT = 0
    }
}
