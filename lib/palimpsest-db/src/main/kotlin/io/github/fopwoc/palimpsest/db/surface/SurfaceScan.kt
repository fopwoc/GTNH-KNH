package io.github.fopwoc.palimpsest.db.surface

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.SectionBlocks

/**
 * What a chunk looks like from [ceiling] downward, by the display mapping's [BlockKind]s alone. Air
 * and transparent blocks are looked through. Water is looked through too: the block is the floor,
 * the height the floor's, and the depth counts the water on top, so shores stay continuous and the
 * seabed keeps its relief; any other liquid is a surface of its own. A decoration (a flower, a
 * slab, a machine part) is the block but keeps the height of what it stands on; under water it is
 * skipped. The ceiling makes the same scan give the surface and a cave level.
 */
internal object SurfaceScan {
    private const val MAX_DEPTH = 255

    /**
     * How far down a stack of decorations is followed for the ground: a fence on a wall on a slab.
     */
    private const val MAX_DECORATION_STACK = 8

    /**
     * [sections] are block id raws bottom-up from [minSection]; [kind] maps a block id raw to its
     * kind.
     */
    fun scan(
        sections: Sections,
        minSection: Int,
        biomes: IntArray,
        kind: (Int) -> BlockKind,
        ceiling: Int = Int.MAX_VALUE,
    ): Surface {
        val minY = minSection * 16
        val top = topOf(sections, minY).coerceAtMost(ceiling)
        val block = IntArray(Surface.COLUMNS)
        val height = IntArray(Surface.COLUMNS)
        val depth = IntArray(Surface.COLUMNS)
        val column = Column(sections, minY, kind)
        for (at in 0 until Surface.COLUMNS) {
            column.scan(at and 15, at shr 4, top)
            block[at] = column.block
            height[at] = column.height
            depth[at] = column.depth
        }
        return Surface(block, height, depth, biomes.copyOf())
    }

    /**
     * The highest Y of the highest section holding anything, or below the chunk when it is empty.
     */
    private fun topOf(sections: Sections, minY: Int): Int {
        for (index in sections.size - 1 downTo 0) if (sections.has(index))
            return minY + index * 16 + 15
        return minY - 1
    }

    /** One column walk; results land in [block], [height] and [depth]. Reused across columns. */
    private class Column(
        private val sections: Sections,
        private val minY: Int,
        private val kind: (Int) -> BlockKind,
    ) {
        var block = 0
        var height = 0
        var depth = 0

        fun scan(x: Int, z: Int, top: Int) {
            var y = top
            var water = 0
            var waterTop = Int.MIN_VALUE
            while (y >= minY) {
                val section = sections[(y - minY) shr 4]
                if (section == null) {
                    y = minY + ((y - minY) shr 4) * 16 - 1
                    continue
                }
                val id = section[SectionBlocks.index(x, (y - minY) and 15, z)]
                when (kind(id)) {
                    BlockKind.AIR,
                    BlockKind.TRANSPARENT -> Unit
                    BlockKind.WATER -> {
                        if (waterTop == Int.MIN_VALUE) waterTop = y
                        if (water < MAX_DEPTH) water++
                    }
                    BlockKind.DECORATION ->
                        if (water == 0) return found(id, groundBelow(x, y, z), 0)
                    BlockKind.LIQUID,
                    BlockKind.SOLID -> return found(id, y, water)
                }
                y--
            }
            // Water all the way down, or nothing: the water itself is the surface.
            if (waterTop != Int.MIN_VALUE) found(blockAt(x, waterTop, z), waterTop, water)
            else found(0, minY, 0)
        }

        private fun found(block: Int, height: Int, depth: Int) {
            this.block = block
            this.height = height
            this.depth = depth
        }

        private fun blockAt(x: Int, y: Int, z: Int): Int =
            sections[(y - minY) shr 4]?.get(SectionBlocks.index(x, (y - minY) and 15, z)) ?: 0

        /** The first non-decoration block under a decoration, or the decoration's own foot. */
        private fun groundBelow(x: Int, y: Int, z: Int): Int {
            var below = y - 1
            while (below >= minY && below > y - MAX_DECORATION_STACK) {
                if (!sections.has((below - minY) shr 4)) return below
                val kind = kind(blockAt(x, below, z))
                if (
                    kind != BlockKind.AIR &&
                        kind != BlockKind.TRANSPARENT &&
                        kind != BlockKind.DECORATION
                )
                    return below
                below--
            }
            return (y - 1).coerceAtLeast(minY)
        }
    }
}
