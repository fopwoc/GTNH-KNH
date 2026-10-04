package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MapPageStoreTest {
    @Test
    fun liveLooksCoverHistoryAndHistoryAnswersForPastTicks() =
        TestHistory.with("palimpsest-store-") { history ->
            val tile = TileKey(2, 3)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            history.stage(tile.x, tile.z, "red")
            history.commit(100)
            MapPageStore(history.blocks, history.tiles()).use { store ->
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(store.latest(page)).colorAt(32, 48),
                )
                assertNull(store.latest(MapPageKey(5, 5, 0)))
                // What the scanner sees shows at once, long before it reaches history.
                store.observe(tile, TestBlocks.flat(2))
                assertEquals(
                    TestBlocks.shown(TestBlocks.BLUE),
                    assertNotNull(store.latest(page)).colorAt(32, 48),
                )
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(store.historical(page, 100)).colorAt(32, 48),
                )
                history.stage(tile.x, tile.z, "green")
                history.commit(200)
                assertEquals(
                    TestBlocks.shown(TestBlocks.GREEN),
                    assertNotNull(store.historical(page, 200)).colorAt(32, 48),
                )
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(store.historical(page, 199)).colorAt(32, 48),
                )
                assertNull(store.historical(page, 99))
            }
        }

    @Test
    fun zoomedOutPagesReadTheOverviewAndFollowCommits() =
        TestHistory.with("palimpsest-store-lod-") { history ->
            for (z in 0 until 4) for (x in 0 until 4) history.stage(
                x,
                z,
                if ((x + z) % 2 == 0) "red" else "blue",
            )
            history.commit(100)
            MapPageStore(history.blocks, history.tiles()).use { store ->
                val lod4 = MapPageKey.containingTile(0, 0, 4)
                val page = assertNotNull(store.latest(lod4))
                assertEquals(TestBlocks.shown(TestBlocks.RED), page.colorAt(0, 0))
                assertEquals(TestBlocks.shown(TestBlocks.BLUE), page.colorAt(1, 0))
                assertEquals(0, page.colorAt(4, 0) ushr 24)
                val lod6 = MapPageKey.containingTile(0, 0, 6)
                assertEquals(0xFF, assertNotNull(store.latest(lod6)).colorAt(0, 0) ushr 24)
                history.stage(0, 0, "green")
                history.commit(200)
                assertEquals(
                    TestBlocks.shown(TestBlocks.GREEN),
                    assertNotNull(store.historical(lod4, 200)).colorAt(0, 0),
                )
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(store.historical(lod4, 199)).colorAt(0, 0),
                )
            }
        }
}
