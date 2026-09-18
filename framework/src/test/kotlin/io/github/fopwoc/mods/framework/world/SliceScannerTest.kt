package io.github.fopwoc.mods.framework.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SliceScannerTest {
    private companion object {
        const val STONE = 0xFF808080.toInt()
        const val GRASS = 0xFF5FA83A.toInt()
        const val WATER = 0xFF3F5FDF.toInt()
        const val DIRT = 0xFF8B6B47.toInt()
        val palette = WorldPalette.derive(listOf(STONE, GRASS, WATER, DIRT))
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

        override fun colorAt(x: Int, y: Int, z: Int): Int {
            lookups++
            return blocks[y][z * 16 + x]
        }

        override fun isLiquid(x: Int, y: Int, z: Int): Boolean = blocks[y][z * 16 + x] == WATER
    }

    private fun shaded(color: Int, shade: Int) =
        WorldPalette.shade(color, WorldPalette.SHADES[shade])

    private fun SliceScanner.Result.paletteColor(x: Int, z: Int): Int =
        palette.argb(colors[z * 16 + x].toInt() and 255)

    @Test
    fun flatSurfaceIsNormalShadeAndCostsOneLookupPerColumn() {
        val world = FakeColumns()
        for (y in 0..63) world.fill(y, STONE)
        world.fill(64, GRASS)
        val result = SliceScanner.scan(world, 255, palette)
        assertTrue(result.argb.all { it == shaded(GRASS, 1) })
        assertTrue((0 until 256).all { result.paletteColor(it % 16, it / 16) == shaded(GRASS, 1) })
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
        val result = SliceScanner.scan(world, 255, palette)
        assertEquals(shaded(DIRT, 1), result.argb[3])
        assertEquals(shaded(DIRT, 2), result.argb[4 * 16 + 3])
        assertEquals(shaded(DIRT, 0), result.argb[12 * 16 + 3])
        assertEquals(shaded(DIRT, 2), result.paletteColor(3, 4))
        // With the northern chunk's heights known, the first row shades as a slope too.
        val continued = SliceScanner.scan(world, 255, palette, IntArray(16) { 60 })
        assertEquals(shaded(DIRT, 2), continued.argb[3])
    }

    @Test
    fun waterShadesByDepthAndTransparentBlocksAreSkipped() {
        val world = FakeColumns()
        for (y in 0..50) world.fill(y, STONE)
        for (x in 0 until 16) for (y in 51..(51 + x)) world.set(x, y, 0, WATER)
        for (x in 0 until 16) world.set(x, 60, 8, STONE)
        val result = SliceScanner.scan(world, 255, palette)
        assertEquals(shaded(WATER, 2), result.argb[0])
        assertEquals(shaded(WATER, 0), result.argb[15])
        assertEquals(60, result.heights[8 * 16 + 4])
        // Row 8 sits ten blocks above row 7, so it shades as an upward slope.
        assertEquals(shaded(STONE, 2), result.argb[8 * 16 + 4])
    }

    @Test
    fun ceilingRevealsCavesAndSkipsEmptySectionsAbove() {
        val world = FakeColumns()
        for (y in 0..70) world.fill(y, STONE)
        // A cave at y 30..35 under column (4, 4) with a dirt floor.
        for (y in 30..35) world.set(4, y, 4, 0)
        world.set(4, 29, 4, DIRT)
        val surface = SliceScanner.scan(world, 255, palette)
        assertEquals(shaded(STONE, 1), surface.argb[4 * 16 + 4])
        val cave = SliceScanner.scan(world, 33, palette)
        assertEquals(29, cave.heights[4 * 16 + 4])
        assertEquals(33, cave.heights[0])
        // The cave floor is four blocks below its northern neighbour, so it shades dark.
        assertEquals(shaded(DIRT, 0), cave.argb[4 * 16 + 4])
        // A slice through the open air above the surface finds the surface without scanning air.
        world.lookups = 0
        val sky = SliceScanner.scan(world, 200, palette)
        assertTrue(sky.argb.all { it == shaded(STONE, 1) })
        assertEquals(ChunkColumns.COLUMNS, world.lookups)
    }
}
