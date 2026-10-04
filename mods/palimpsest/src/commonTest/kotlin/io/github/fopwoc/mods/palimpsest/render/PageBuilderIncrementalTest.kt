package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PageBuilderIncrementalTest {
    @Test
    fun partialReadsMatchFreshPagesAtAllNearLodsIncludingNegativePageHalosAndRemovedTiles() {
        val shader = TerrainShader({ it * 7919 and 0xFFFFFF }, { it % 3 }, { 0xFFFFFF })
        for (lod in 0..3) {
            val key = MapPageKey(-1, -1, lod)
            val across = MapPageKey.BASE_TILES shl lod
            val source = Source()
            val keys =
                listOf(
                    TileKey(-across, -across),
                    TileKey(-across - 1, -across),
                    TileKey(-across, -across - 1),
                    TileKey(-1, -1),
                    TileKey(-across + 2, -across + 3),
                )
            keys.forEachIndexed { at, tile ->
                source.records[tile] = TileRecord.solid(1, at + 1, 60)
            }
            val builder = PageBuilder(source, shader)
            var previous = builder.rebuild(key, Long.MAX_VALUE, null, null)
            repeat(20) { step ->
                val tile = keys[step % keys.size]
                if (step % 7 == 0) source.records.remove(tile)
                else
                    source.records[tile] =
                        TileRecord.solid(
                            step + 2L,
                            step + 1,
                            50 + step,
                            depth = step % 9,
                            biome = step,
                        )
                source.reads.clear()
                val next = builder.rebuild(key, Long.MAX_VALUE, previous, setOf(tile))
                assertEquals(listOf(tile), source.reads)
                val full = assertNotNull(builder.build(key, Long.MAX_VALUE))
                assertContentEquals(full.copyPixels(), checkNotNull(next.raster).copyPixels())
                previous = next
            }
            source.records.clear()
            val empty = builder.rebuild(key, Long.MAX_VALUE, previous, keys.toSet())
            assertNull(empty.raster)
            assertNull(empty.grid)
            source.records[keys.first()] = TileRecord.solid(100, 1, 90)
            val restored = builder.rebuild(key, Long.MAX_VALUE, empty, setOf(keys.first()))
            assertContentEquals(
                assertNotNull(builder.build(key, Long.MAX_VALUE)).copyPixels(),
                checkNotNull(restored.raster).copyPixels(),
            )
        }
    }

    private class Source : TileSource {
        val records = HashMap<TileKey, TileRecord>()
        val reads = ArrayList<TileKey>()
        override val latestEpoch = 1L

        override fun tile(key: TileKey, epoch: Long): TileRecord? {
            reads += key
            return records[key]
        }

        override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long) =
            LongArray(side * side) { Sample.NONE.packed }
    }
}
