package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PageBuilderTest {
    private val shader = TerrainShader({ id -> id * 0x010101 }, { false }, { 0xFFFFFF })

    private fun shown(id: Int) =
        TerrainShader.shade((id * 0x010101) or (0xFF shl 24), TerrainShader.SHADES[1])

    @Test
    fun pagesAtEveryLodShowTheRightSquareIncludingNegativeCoordinates() {
        val directory = Files.createTempDirectory("palimpsest-pages-")
        try {
            MapTree(directory, machineId = 1).use { tree ->
                val builder = PageBuilder(tree, shader)
                // Tiles in a 4×4 block around the origin, ids by position; flat heights.
                val tiles = HashMap<TileKey, TileRecord>()
                for (z in -2 until 2) for (x in -2 until 2) {
                    tiles[TileKey(x, z)] =
                        TileRecord.build(
                            7,
                            { position ->
                                10 +
                                    (x + 2) * 4 +
                                    (z + 2) +
                                    (if (position == TileRecord.CENTER) 100 else 0)
                            },
                            { 64 },
                        )
                }
                tree.commit(7, tiles)
                // LOD 0: page (-1, -1) covers tiles -8..-1; tile (-1, -1) is its last tile.
                val lod0 = assertNotNull(builder.build(MapPageKey(-1, -1, 0), Long.MAX_VALUE))
                assertEquals(shown(10 + 1 * 4 + 1), lod0.colorAt(112, 112))
                assertEquals(shown(10 + 1 * 4 + 1 + 100), lod0.colorAt(120, 120))
                assertEquals(0, lod0.colorAt(0, 0) ushr 24)
                val lod0Positive = assertNotNull(builder.build(MapPageKey(0, 0, 0), Long.MAX_VALUE))
                assertEquals(shown(10 + 2 * 4 + 2), lod0Positive.colorAt(0, 0))
                assertEquals(shown(10 + 3 * 4 + 3), lod0Positive.colorAt(31, 31))
                // LOD 2: 4 pixels per tile; the centre sample of each cell.
                val lod2 = assertNotNull(builder.build(MapPageKey(0, 0, 2), Long.MAX_VALUE))
                assertEquals(shown(10 + 2 * 4 + 2), lod2.colorAt(0, 0))
                assertEquals(shown(10 + 3 * 4 + 3), lod2.colorAt(7, 7))
                assertEquals(0, lod2.colorAt(8, 8) ushr 24)
                // LOD 4: one pixel per tile, taken from the parents' samples (tile centre pixel).
                val lod4 = assertNotNull(builder.build(MapPageKey(0, 0, 4), Long.MAX_VALUE))
                assertEquals(shown(10 + 2 * 4 + 2 + 100), lod4.colorAt(0, 0))
                assertEquals(shown(10 + 3 * 4 + 3 + 100), lod4.colorAt(1, 1))
                val lod4Negative =
                    assertNotNull(builder.build(MapPageKey(-1, -1, 4), Long.MAX_VALUE))
                assertEquals(shown(10 + 0 * 4 + 0 + 100), lod4Negative.colorAt(126, 126))
                assertEquals(shown(10 + 1 * 4 + 1 + 100), lod4Negative.colorAt(127, 127))
                // LOD 5: one pixel per 2×2 tiles; the square's sample is its north-west tile's.
                val lod5 = assertNotNull(builder.build(MapPageKey(0, 0, 5), Long.MAX_VALUE))
                assertEquals(shown(10 + 2 * 4 + 2 + 100), lod5.colorAt(0, 0))
                assertEquals(0, lod5.colorAt(1, 1) ushr 24)
                val lod12 = assertNotNull(builder.build(MapPageKey(0, 0, 12), Long.MAX_VALUE))
                assertEquals(shown(10 + 2 * 4 + 2 + 100), lod12.colorAt(0, 0))
                assertNull(builder.build(MapPageKey(3, 3, 12), Long.MAX_VALUE))
                assertNull(builder.build(MapPageKey(0, 0, 4), 6))
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
