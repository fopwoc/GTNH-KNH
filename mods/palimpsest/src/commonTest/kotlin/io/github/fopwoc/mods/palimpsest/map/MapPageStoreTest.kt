package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MapPageStoreTest {
    @Test
    fun disablingHistoryOverwritesCurrentLayerAndResumingAddsOneSnapshot() =
        TestBlocks.withDirectory("palimpsest-current-") { directory ->
            val map = directory.resolve("map")
            val tile = TileKey(-1, 2)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            val distant = MapPageKey.containingTile(tile.x, tile.z, 4)
            var now = 10_000L
            MapPageStore(map, TestBlocks.table(directory), clock = { now }).use { store ->
                repeat(2) { store.observe(tile, TestBlocks.flat(1)) }
                assertEquals(1, store.commitDue())
                assertEquals(1, store.tree.roots.size)
            }

            MapPageStore(map, TestBlocks.table(directory), clock = { now }, historyEnabled = false)
                .use { store ->
                    now += 61_000
                    repeat(2) { store.observe(tile, TestBlocks.flat(2)) }
                    assertEquals(1, store.commitDue())
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(store.latest(page)).colorAt(112, 32),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(store.latest(distant)).colorAt(127, 2),
                    )
                    now += 61_000
                    repeat(2) { store.observe(tile, TestBlocks.flat(3)) }
                    assertEquals(1, store.commitDue())
                    assertEquals(1, store.tree.roots.size)
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.latest(page)).colorAt(112, 32),
                    )
                    assertNull(store.historical(page, now))
                }
            Files.walk(map.resolve("current")).use { files ->
                assertEquals(1, files.filter { it.toString().endsWith(".tile") }.count())
            }

            MapPageStore(map, TestBlocks.table(directory), clock = { now }, historyEnabled = false)
                .use { store ->
                    assertEquals(1, store.tree.roots.size)
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.latest(page)).colorAt(112, 32),
                    )
                }
            MapPageStore(map, TestBlocks.table(directory), clock = { now }).use { store ->
                assertEquals(2, store.tree.roots.size)
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(store.historical(page, 10_000L)).colorAt(112, 32),
                )
                assertEquals(
                    TestBlocks.shown(TestBlocks.GREEN),
                    assertNotNull(store.latest(page)).colorAt(112, 32),
                )
            }
        }

    @Test
    fun currentLayerKeepsTheFirstPresentSampleAtDistantZoom() =
        TestBlocks.withDirectory("palimpsest-current-sample-") { directory ->
            val map = directory.resolve("map")
            var now = 10_000L
            MapPageStore(map, TestBlocks.table(directory), clock = { now }).use { store ->
                repeat(2) { store.observe(TileKey(0, 0), TestBlocks.flat(1)) }
                store.commitDue()
            }
            MapPageStore(map, TestBlocks.table(directory), clock = { now }, historyEnabled = false)
                .use { store ->
                    now += 61_000
                    repeat(2) { store.observe(TileKey(1, 0), TestBlocks.flat(2)) }
                    store.commitDue()
                    val page = MapPageKey.containingTile(0, 0, 5)
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.latest(page)).colorAt(0, 0),
                    )
                    now += 61_000
                    store.observe(TileKey(0, 0), TestBlocks.flat(3))
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.latest(page)).colorAt(0, 0),
                    )
                    store.observe(TileKey(0, 0), TestBlocks.flat(3))
                    store.commitDue()
                    assertEquals(
                        TestBlocks.shown(TestBlocks.GREEN),
                        assertNotNull(store.latest(page)).colorAt(0, 0),
                    )
                }
        }

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
                    commitInterval = { Duration.ofSeconds(60) },
                    clock = { now },
                )
                .use { store ->
                    store.observe(tile, TestBlocks.flat(1))
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.latest(page)).colorAt(32, 48),
                    )
                    assertNull(store.latest(MapPageKey(5, 5, 0)))
                    store.observe(tile, TestBlocks.flat(1))
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
                    store.observe(tile, TestBlocks.flat(2))
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
                        TestBlocks.shown(TestBlocks.BLUE),
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
                    commitInterval = { Duration.ofSeconds(60) },
                    clock = { now },
                )
                .use { store ->
                    for (z in 0 until 4) for (x in 0 until 4) store.observe(
                        TileKey(x, z),
                        TestBlocks.flat(1 + (x + z) % 2),
                    )
                    for (z in 0 until 4) for (x in 0 until 4) store.observe(
                        TileKey(x, z),
                        TestBlocks.flat(1 + (x + z) % 2),
                    )
                    val lod4 = MapPageKey.containingTile(0, 0, 4)
                    // Nothing is committed yet, but the far view overlays what the broker holds.
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(store.latest(lod4)).colorAt(0, 0),
                    )
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
