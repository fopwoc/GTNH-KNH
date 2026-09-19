package io.github.fopwoc.mods.framework.world

/**
 * Read access to one chunk's blocks for top-down scanning, independent of Minecraft classes.
 *
 * Coordinates are chunk-local (0..15) and world Y. Blocks are reported as the map's block ids;
 * [TRANSPARENT] means the map looks through the block: air, glass, torches, tall grass.
 */
interface ChunkColumns {
    /** Highest block Y the chunk can hold. */
    val topY: Int

    /** Highest Y that blocks sky light in the column, or -1 if the column is entirely open. */
    fun surfaceY(x: Int, z: Int): Int

    /** True when no block in the 16-block band starting at `section * 16` exists. */
    fun isSectionEmpty(section: Int): Boolean

    /** The map's id for the block, or [TRANSPARENT]. */
    fun blockAt(x: Int, y: Int, z: Int): Int

    fun isLiquid(x: Int, y: Int, z: Int): Boolean

    /** Biome id of the column, 0..65535 (vanilla stops at 255; EndlessIDs goes further). */
    fun biomeAt(x: Int, z: Int): Int

    companion object {
        const val SIDE = 16
        const val COLUMNS = SIDE * SIDE
        const val TRANSPARENT = 0
    }
}
