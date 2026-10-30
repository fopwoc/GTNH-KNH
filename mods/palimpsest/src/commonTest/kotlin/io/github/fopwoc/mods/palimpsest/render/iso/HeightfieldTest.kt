package io.github.fopwoc.mods.palimpsest.render.iso

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeightfieldTest {
    private val shader = TerrainShader({ 0x808080 }, { 0 }, { 0xFFFFFF })
    /** Tile (5, -2) seen with water three deep; the tile east of it never seen. */
    private val tiles =
        arrayOf<TileRecord?>(TileRecord.solid(0, 1, height = 60, depth = 3, biome = 1), null)

    @Test
    fun tilesStandAsColumnsWithTheirWaterOnTop() {
        val field = Heightfield.of(tiles, 5, -2, 2, 1, 1, shader)
        assertEquals(32, field.columns)
        assertEquals(16, field.rows)
        assertEquals(64, field.topAt(5 * 16 + 3, -2 * 16 + 7))
        assertNull(field.topAt(6 * 16 + 3, -2 * 16 + 7))
        assertTrue(field.present(15, 15))
        assertFalse(field.present(16, 0))
    }

    @Test
    fun widerCellsPickTheirMiddleColumn() {
        val field = Heightfield.of(tiles, 5, -2, 2, 1, 8, shader)
        assertEquals(4, field.columns)
        assertEquals(2, field.rows)
        assertEquals(64, field.top(1, 1))
        assertFalse(field.present(2, 0))
        assertTrue(field.covers(80, -32, 112, -16, 8))
        assertFalse(field.covers(80, -32, 113, -16, 8))
    }
}
