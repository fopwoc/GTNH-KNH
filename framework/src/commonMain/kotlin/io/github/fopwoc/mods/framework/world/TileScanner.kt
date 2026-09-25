package io.github.fopwoc.mods.framework.world

/**
 * What a chunk looks like from [ceiling] downward, as facts per column: the first block the map
 * does not look through, its height, how deep the water above it is, and the biome. Water is looked
 * through: the block is the floor, the height the floor's, and the depth counts the water on top,
 * so shores stay continuous and the seabed keeps its relief; any other liquid is a surface of its
 * own. A decoration (a flower, a slab, a machine part) is the block but keeps the height of what it
 * stands on. The ceiling makes the same scan give the surface at 255 and a cave level at 40. No
 * colors and no shading happen here; the map renders those from the facts.
 */
object TileScanner {
    class Scan(val block: IntArray, val height: IntArray, val depth: IntArray, val biome: IntArray)

    private const val MAX_DEPTH = 255
    /**
     * How far down a stack of decorations is followed for the ground: a fence on a wall on a slab.
     */
    private const val MAX_DECORATION_STACK = 8

    fun scan(columns: ChunkColumns, ceiling: Int): Scan {
        require(ceiling in columns.bottomY..columns.topY)
        val block = IntArray(ChunkColumns.COLUMNS)
        val height = IntArray(ChunkColumns.COLUMNS)
        val depth = IntArray(ChunkColumns.COLUMNS)
        val biome = IntArray(ChunkColumns.COLUMNS)
        for (z in 0 until ChunkColumns.SIDE) for (x in 0 until ChunkColumns.SIDE) {
            val at = z * ChunkColumns.SIDE + x
            biome[at] = columns.biomeAt(x, z)
            val top = topBlock(columns, x, z, ceiling)
            block[at] = top.block
            height[at] = top.height
            depth[at] = top.waterDepth
        }
        return Scan(block, height, depth, biome)
    }

    private class Top(val block: Int, val height: Int, val waterDepth: Int)

    private fun topBlock(columns: ChunkColumns, x: Int, z: Int, ceiling: Int): Top {
        // Nothing with a color sits above the sky-light surface, so start at the lower of the two.
        var y = minOf(ceiling, columns.surfaceY(x, z))
        var depth = 0
        var waterTop: Int? = null
        while (y >= columns.bottomY) {
            if (columns.isSectionEmpty(y shr 4)) {
                y = (y shr 4) * ChunkColumns.SIDE - 1
                continue
            }
            val block = columns.blockAt(x, y, z)
            when {
                block == ChunkColumns.TRANSPARENT -> Unit
                columns.isWater(x, y, z) -> {
                    if (waterTop == null) waterTop = y
                    if (depth < MAX_DEPTH) depth++
                }
                // Under water a plant is not the floor either; above it, it shows at ground height.
                columns.isDecoration(x, y, z) ->
                    if (depth == 0) return Top(block, groundBelow(columns, x, y, z), 0)
                else -> return Top(block, y, depth)
            }
            y--
        }
        // Water all the way down, or nothing: the water itself is the surface.
        return if (waterTop != null) Top(columns.blockAt(x, waterTop, z), waterTop, depth)
        else Top(ChunkColumns.TRANSPARENT, columns.bottomY, 0)
    }

    /** The first non-decoration block under a decoration, or the decoration's own foot. */
    private fun groundBelow(columns: ChunkColumns, x: Int, y: Int, z: Int): Int {
        var below = y - 1
        while (below >= columns.bottomY && below > y - MAX_DECORATION_STACK) {
            if (columns.isSectionEmpty(below shr 4)) return below
            val block = columns.blockAt(x, below, z)
            if (block != ChunkColumns.TRANSPARENT && !columns.isDecoration(x, below, z))
                return below
            below--
        }
        return (y - 1).coerceAtLeast(columns.bottomY)
    }
}
