package io.github.fopwoc.mods.framework.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SliceScannerTest {
    private companion object {
        const val STONE = 11
        const val GRASS = 1
        const val WATER = 12
        const val DIRT = 10
    }

    /** A 16×256×16 array world; color 0 is air, [WATER] is liquid. Counts lookups. */
    private class FakeColumns(override val topY: Int = 255) : ChunkColumns {
        val blocks = Array(256) { IntArray(ChunkColumns.COLUMNS) }
        var lookups = 0

        fun fill(y: Int, color: Int) = blocks[y].fill(color)

        fun set(x: Int, y: Int, z: Int, color: Int) {
            blocks[y][z * 16 + x] = color
        }

        override fun surfaceY(x: Int, z: Int): Int {
            for (y in topY downTo 0) if (blocks[y][z * 16 + x] != 0) return y
            return -1
        }

        override fun isSectionEmpty(section: Int): Boolean =
            (0 until 16).all { blocks[section * 16 + it].all { color -> color == 0 } }

        override fun colorIndex(x: Int, y: Int, z: Int): Int {
            lookups++
            return blocks[y][z * 16 + x]
        }

        override fun isLiquid(x: Int, y: Int, z: Int): Boolean = blocks[y][z * 16 + x] == WATER
    }

    @Test
    fun flatSurfaceIsNormalShadeAndCostsOneLookupPerColumn() {
        val world = FakeColumns()
        for (y in 0..63) world.fill(y, STONE)
        world.fill(64, GRASS)
        val result = SliceScanner.scan(world, 255)
        assertTrue(result.colors.all { it.toInt() == GRASS * 4 + 1 })
        assertTrue(result.heights.all { it == 64 })
        assertEquals(ChunkColumns.COLUMNS, world.lookups)
    }

    @Test
    fun slopesShadeLighterGoingUpSouthAndDarkerGoingDown() {
        val world = FakeColumns()
        for (y in 0..60) world.fill(y, STONE)
        // Rows 0..7 climb one block per row, rows 8..15 descend.
        for (z in 0 until 16) for (x in 0 until 16) {
            val height = 61 + (if (z < 8) z else 15 - z)
            for (y in 61..height) world.set(x, y, z, DIRT)
        }
        val result = SliceScanner.scan(world, 255)
        fun shade(x: Int, z: Int) = result.colors[z * 16 + x].toInt() and 3
        assertEquals(1, shade(3, 0))
        assertEquals(2, shade(3, 4))
        assertEquals(0, shade(3, 12))
        // With the northern chunk's heights known, the first row shades as a slope too.
        val north = IntArray(16) { 60 }
        val continued = SliceScanner.scan(world, 255, north)
        assertEquals(2, continued.colors[3].toInt() and 3)
    }

    @Test
    fun waterShadesByDepthAndTransparentBlocksAreSkipped() {
        val world = FakeColumns()
        for (y in 0..50) world.fill(y, STONE)
        for (x in 0 until 16) {
            for (y in 51..(51 + x)) world.set(x, y, 0, WATER)
        }
        world.set(0, 70, 5, 0) // air above transparent nothing
        world.fill(52, 0)
        for (x in 0 until 16) world.set(x, 60, 8, STONE)
        val result = SliceScanner.scan(world, 255)
        fun shade(x: Int) = result.colors[x].toInt() and 3
        assertEquals(WATER, result.colors[0].toInt() shr 2)
        assertEquals(2, shade(0))
        assertEquals(0, shade(15))
        assertEquals(60, result.heights[8 * 16 + 4])
    }

    @Test
    fun ceilingRevealsCavesAndSkipsEmptySectionsAbove() {
        val world = FakeColumns()
        for (y in 0..70) world.fill(y, STONE)
        // A cave at y 30..35 under column (4, 4) with a dirt floor.
        for (y in 30..35) world.set(4, y, 4, 0)
        world.set(4, 29, 4, DIRT)
        val surface = SliceScanner.scan(world, 255)
        assertEquals(STONE, surface.colors[4 * 16 + 4].toInt() shr 2)
        val cave = SliceScanner.scan(world, 33)
        assertEquals(DIRT, cave.colors[4 * 16 + 4].toInt() shr 2)
        assertEquals(29, cave.heights[4 * 16 + 4])
        assertEquals(STONE, cave.colors[0].toInt() shr 2)
        assertEquals(33, cave.heights[0])
        // A slice through the open air above the surface finds nothing without scanning it.
        world.lookups = 0
        val sky = SliceScanner.scan(world, 200)
        assertTrue(sky.colors.all { it.toInt() == STONE * 4 + 1 })
        assertEquals(ChunkColumns.COLUMNS, world.lookups)
    }

    @Test
    fun paletteHasVanillaShadesAndTransparentZero() {
        val base = IntArray(64) { if (it == 0) 0 else 0x808080 }
        val palette = MapPalette.build(base)
        assertEquals(256, palette.size)
        assertEquals(0, palette[0])
        assertEquals(0xFF5A5A5A.toInt(), palette[4 * 4 + 0])
        assertEquals(0xFF6E6E6E.toInt(), palette[4 * 4 + 1])
        assertEquals(0xFF808080.toInt(), palette[4 * 4 + 2])
        assertEquals(0xFF434343.toInt(), palette[4 * 4 + 3])
    }
}
