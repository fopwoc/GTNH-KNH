package io.github.fopwoc.mods.framework.world

/**
 * Read access to one chunk's blocks for top-down scanning, independent of Minecraft classes.
 *
 * Coordinates are chunk-local (0..15) and world Y. Colors are vanilla map color indexes (0..63),
 * where 0 means "transparent for the map": air, glass, torches, tall grass and the like.
 */
interface ChunkColumns {
    /** Highest block Y the chunk can hold. */
    val topY: Int

    /** Highest Y that blocks sky light in the column, or -1 if the column is entirely open. */
    fun surfaceY(x: Int, z: Int): Int

    /** True when no block in the 16-block band starting at `section * 16` exists. */
    fun isSectionEmpty(section: Int): Boolean

    fun colorIndex(x: Int, y: Int, z: Int): Int

    fun isLiquid(x: Int, y: Int, z: Int): Boolean

    companion object {
        const val SIDE = 16
        const val COLUMNS = SIDE * SIDE
        const val TRANSPARENT = 0
        const val COLOR_INDEXES = 64
    }
}
