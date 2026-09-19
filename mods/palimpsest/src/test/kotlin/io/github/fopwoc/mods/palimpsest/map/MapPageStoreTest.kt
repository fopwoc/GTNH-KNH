package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MapPageStoreTest {
    @Test
    fun observationsRenderLiveAndCommitOnScheduleAndOnClose() =
        TestBlocks.withDirectory("palimpsest-store-") { directory ->
            val tile = TileKey(2, 3)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            var now = 10_000L
            val blocks = TestBlocks.table(directory)
            MapPageStore(
                    directory.resolve("map"),
                    blocks,
                    commitInterval = Duration.ofSeconds(60),
                    clock = { now },
                )
                .use { store ->
                    store.observe(tile, TestBlocks.flat(1))
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.latest(page)).colorAt(32, 48),
                    )
                    assertNull(store.latest(MapPageKey(5, 5, 0)))
                    assertEquals(1, store.commitDue())
                    now += 1_000
                    store.observe(tile, TestBlocks.flat(2))
                    // Live page shows the new facts although history still holds the old ones.
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(store.latest(page)).colorAt(32, 48),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.historical(page, now)).colorAt(32, 48),
                    )
                    assertEquals(0, store.commitDue())
                    now += 60_000
                    assertEquals(1, store.commitDue())
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(store.historical(page, now)).colorAt(32, 48),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.historical(page, now - 1)).colorAt(32, 48),
                    )
                    store.observe(tile, TestBlocks.flat(3))
                }
            MapPageStore(directory.resolve("map"), TestBlocks.table(directory), clock = { now })
                .use { reopened ->
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(reopened.latest(page)).colorAt(32, 48),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(reopened.historical(page, now)).colorAt(32, 48),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(reopened.historical(page, now - 1)).colorAt(32, 48),
                    )
                    assertNull(reopened.historical(page, 9_000))
                }
        }

    @Test
    fun zoomedOutPagesReadSamplesAndFollowCommits() =
        TestBlocks.withDirectory("palimpsest-store-lod-") { directory ->
            var now = 10_000L
            MapPageStore(
                    directory.resolve("map"),
                    TestBlocks.table(directory),
                    commitInterval = Duration.ofSeconds(60),
                    clock = { now },
                )
                .use { store ->
                    for (z in 0 until 4) for (x in 0 until 4) store.observe(
                        TileKey(x, z),
                        TestBlocks.flat(1 + (x + z) % 2),
                    )
                    val lod4 = MapPageKey.containingTile(0, 0, 4)
                    // Nothing is committed yet, so the far view has nothing to sample.
                    assertNull(store.latest(lod4))
                    assertEquals(16, store.commitDue())
                    val page = assertNotNull(store.latest(lod4))
                    assertEquals(TestBlocks.shown(TestBlocks.RED), page.colorAt(0, 0))
                    assertEquals(TestBlocks.shown(TestBlocks.BLUE), page.colorAt(1, 0))
                    assertEquals(0, page.colorAt(4, 0) ushr 24)
                    val lod6 = MapPageKey.containingTile(0, 0, 6)
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.latest(lod6)).colorAt(0, 0),
                    )
                    now += 61_000
                    store.observe(TileKey(0, 0), TestBlocks.flat(3))
                    assertEquals(1, store.commitDue())
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.latest(lod4)).colorAt(0, 0),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.historical(lod4, now - 1)).colorAt(0, 0),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.historical(lod4, now)).colorAt(0, 0),
                    )
                }
        }
}
