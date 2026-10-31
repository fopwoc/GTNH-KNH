package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MapTileStoreTest {
    @Test
    fun whatTheScanSawCoversHistoryAndListenersHearOfIt() =
        TestHistory.with("palimpsest-store-") { history ->
            val tile = TileKey(2, 3)
            history.stage(tile.x, tile.z, "red")
            history.commit(100)
            MapTileStore(history.blocks, history.tiles()).let { store ->
                val heard = mutableListOf<TileKey>()
                store.addChangeListener { heard += it }
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    TestBlocks.colorOf(store.shader, store.latestTile(tile)),
                )
                assertEquals(0, TestBlocks.colorOf(store.shader, store.latestTile(TileKey(5, 5))))
                // What the scanner sees shows at once, long before it reaches history.
                store.observe(tile, TestBlocks.flat(2))
                assertEquals(
                    TestBlocks.shown(TestBlocks.BLUE),
                    TestBlocks.colorOf(store.shader, store.latestTile(tile)),
                )
                assertEquals(listOf(tile), heard)
                // The same look again is no change.
                store.observe(tile, TestBlocks.flat(2))
                assertEquals(listOf(tile), heard)
            }
        }

    @Test
    fun tilesSpanSeveralReadsAndShowWhatIsNotCommittedYet() =
        TestHistory.with("palimpsest-store-tiles-") { history ->
            history.stage(2, 3, "red")
            history.commit(100)
            MapTileStore(history.blocks, history.tiles()).let { store ->
                // Past the first 128-tile read, and nothing of it in history yet.
                store.observe(TileKey(130, 1), TestBlocks.flat(2))
                val tiles = store.tiles(0, 0, 200, 5)
                assertEquals(1000, tiles.size)
                assertEquals(store.latestTile(TileKey(2, 3)), tiles[3 * 200 + 2])
                assertEquals(TestBlocks.flat(2), tiles[1 * 200 + 130])
                assertEquals(2, tiles.count { it != null })
            }
        }
}
