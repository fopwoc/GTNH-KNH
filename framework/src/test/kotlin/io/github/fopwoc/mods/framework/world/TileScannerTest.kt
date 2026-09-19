package io.github.fopwoc.mods.framework.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileScannerTest {
    private companion object {
        const val STONE = 1
        const val GRASS = 2
        const val WATER = 3
        const val DIRT = 4
        const val FLOWER = 5
    }

    /** A 16×256×16 array world of block ids; 0 is air, [WATER] is liquid. Counts lookups. */
    private class FakeColumns(override val topY: Int = 255) : ChunkColumns {
        val blocks = Array(256) { IntArray(ChunkColumns.COLUMNS) }
        var lookups = 0

        fun fill(y: Int, block: Int) = blocks[y].fill(block)

        fun set(x: Int, y: Int, z: Int, block: Int) {
            blocks[y][z * 16 + x] = block
        }

        override fun surfaceY(x: Int, z: Int): Int {
            for (y in topY downTo 0) if (blocks[y][z * 16 + x] != 0) return y
            return -1
        }

        override fun isSectionEmpty(section: Int): Boolean =
            (0 until 16).all { blocks[section * 16 + it].all { block -> block == 0 } }

        override fun blockAt(x: Int, y: Int, z: Int): Int {
            lookups++
            return blocks[y][z * 16 + x]
        }

        override fun isLiquid(x: Int, y: Int, z: Int): Boolean = blocks[y][z * 16 + x] == WATER

        override fun isDecoration(x: Int, y: Int, z: Int): Boolean = blocks[y][z * 16 + x] == FLOWER

        override fun biomeAt(x: Int, z: Int): Int = if (x < 8) 1 else 6
    }

    @Test
    fun flatSurfaceCostsOneLookupPerColumn() {
        val world = FakeColumns()
        for (y in 0..63) world.fill(y, STONE)
        world.fill(64, GRASS)
        val scan = TileScanner.scan(world, 255)
        assertTrue(scan.block.all { it == GRASS })
        assertTrue(scan.height.all { it == 64 })
        assertTrue(scan.depth.all { it == 0 })
        assertEquals(ChunkColumns.COLUMNS, world.lookups)
        assertEquals(1, scan.biome[0])
        assertEquals(6, scan.biome[15])
    }

    @Test
    fun waterDepthCountsContiguousLiquidAndTransparentBlocksAreSkipped() {
        val world = FakeColumns()
        for (y in 0..50) world.fill(y, STONE)
        for (x in 0 until 16) for (y in 51..(51 + x)) world.set(x, y, 0, WATER)
        for (x in 0 until 16) world.set(x, 60, 8, STONE)
        val scan = TileScanner.scan(world, 255)
        assertEquals(WATER, scan.block[0])
        assertEquals(1, scan.depth[0])
        assertEquals(16, scan.depth[15])
        assertEquals(51 + 15, scan.height[15])
        assertEquals(60, scan.height[8 * 16 + 4])
        assertEquals(STONE, scan.block[8 * 16 + 4])
    }

    @Test
    fun ceilingRevealsCavesAndSkipsEmptySectionsAbove() {
        val world = FakeColumns()
        for (y in 0..70) world.fill(y, STONE)
        for (y in 30..35) world.set(4, y, 4, 0)
        world.set(4, 29, 4, DIRT)
        val surface = TileScanner.scan(world, 255)
        assertEquals(STONE, surface.block[4 * 16 + 4])
        assertEquals(70, surface.height[4 * 16 + 4])
        val cave = TileScanner.scan(world, 33)
        assertEquals(DIRT, cave.block[4 * 16 + 4])
        assertEquals(29, cave.height[4 * 16 + 4])
        assertEquals(33, cave.height[0])
        world.lookups = 0
        val sky = TileScanner.scan(world, 200)
        assertEquals(70, sky.height[0])
        assertEquals(ChunkColumns.COLUMNS, world.lookups)
        val empty = FakeColumns()
        val nothing = TileScanner.scan(empty, 255)
        assertTrue(nothing.block.all { it == ChunkColumns.TRANSPARENT })
        assertTrue(nothing.height.all { it == 0 })
    }

    @Test
    fun decorationsShowButKeepTheGroundHeight() {
        val world = FakeColumns()
        for (y in 0..63) world.fill(y, STONE)
        world.fill(64, GRASS)
        world.set(3, 65, 3, FLOWER)
        world.set(4, 65, 3, FLOWER)
        world.set(4, 66, 3, FLOWER)
        val scan = TileScanner.scan(world, 255)
        assertEquals(FLOWER, scan.block[3 * 16 + 3])
        assertEquals(64, scan.height[3 * 16 + 3])
        assertEquals(FLOWER, scan.block[3 * 16 + 4])
        assertEquals(64, scan.height[3 * 16 + 4])
        assertEquals(GRASS, scan.block[3 * 16 + 5])
    }
}
