package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapViewTest {
    private var now = 50_000L

    @Test
    fun framesNeverBlockAndPagesArriveThenFollowObservationsAndTime() = withStore { store ->
        val changes = AtomicInteger()
        MapView(store, parallelism = 2, onChanged = { changes.incrementAndGet() }).use { view ->
            val tile = TileKey(1, 1)
            store.observe(tile, TestBlocks.flat(1))
            val camera = MapCamera(64.0, 64.0, 1.0, 64, 64)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            assertEquals(listOf(page), camera.visiblePages())

            assertTrue(view.frame(camera).draws.isEmpty())
            awaitIdle(view)
            val first = view.frame(camera).draws.single()
            assertEquals(TestBlocks.shown(TestBlocks.RED), colorOf(store, page, 16, 16))
            assertSame(first.image, view.frame(camera).draws.single().image)
            assertTrue(changes.get() >= 1)

            // A new observation keeps the old page on screen while the replacement builds.
            store.observe(tile, TestBlocks.flat(2))
            assertSame(first.image, view.frame(camera).draws.single().image)
            awaitIdle(view)
            val settled = view.frame(camera).draws.single().image
            store.observe(tile, TestBlocks.flat(2))
            assertEquals(0, view.pendingCount())
            assertSame(settled, view.frame(camera).draws.single().image)
            val second = view.frame(camera).draws.single()
            assertTrue(first.image !== second.image)
            assertEquals(TestBlocks.shown(TestBlocks.BLUE), colorOf(store, page, 16, 16))

            // History as of a moment before the commit shows the committed facts.
            assertEquals(1, store.commitDue())
            now += 60_000
            store.observe(tile, TestBlocks.flat(3))
            assertEquals(1, store.commitDue())
            view.frame(camera)
            awaitIdle(view)
            val live = view.frame(camera).draws.single().image
            assertEquals(0, view.pendingCount())
            assertSame(live, view.frame(camera, MapTime.At(now - 1)).draws.single().image)
            awaitIdle(view)
            val historical = assertNotNull(store.historical(page, now - 1))
            assertEquals(TestBlocks.shown(TestBlocks.BLUE), historical.colorAt(16, 16))
            val scrubbed = view.frame(camera, MapTime.At(now - 1)).draws.single().image
            assertTrue(scrubbed !== live)
            assertSame(historical.image, scrubbed)
            assertSame(scrubbed, view.frame(camera).draws.single().image)
            awaitIdle(view)
            assertEquals(TestBlocks.shown(TestBlocks.GREEN), colorOf(store, page, 16, 16))
            assertTrue(view.frame(camera).draws.single().image !== scrubbed)
        }
    }

    @Test
    fun pagesScrolledOutOfViewAreNotBuilt() = withStore { store ->
        for (x in 0 until 64) store.observe(TileKey(x * 8, 0), TestBlocks.flat(1))
        MapView(store, parallelism = 1).use { view ->
            val wide = MapCamera(4096.0, 64.0, 0.25, 2048, 64)
            assertTrue(wide.visiblePages().size > 8)
            assertTrue(view.frame(wide).draws.isEmpty())
            val narrow = MapCamera(64.0, 64.0, 1.0, 64, 64)
            view.frame(narrow)
            awaitIdle(view)
            assertEquals(1, view.frame(narrow).draws.size)
            assertEquals(0, view.pendingCount())
        }
    }

    @Test
    fun cachedNeighbouringLevelsStandInWhileTheNewLevelBuilds() = withStore { store ->
        store.observe(TileKey(1, 1), TestBlocks.flat(1))
        MapView(store, parallelism = 1).use { view ->
            val close = MapCamera(64.0, 64.0, 1.0, 64, 64)
            val fine = MapPageKey.containingTile(1, 1, 0)
            view.frame(close)
            awaitIdle(view)
            assertEquals(fine, MapPageKey(0, 0, 0))

            // Zooming out: the level-0 page fills in under the level-1 page until it is built.
            val far = MapCamera(64.0, 64.0, 0.5, 64, 64)
            val coarse = MapPageKey(0, 0, 1)
            assertEquals(listOf(coarse), far.visiblePages())
            val standIn = view.frame(far).draws.single()
            assertSame(store.latest(fine)!!.image, standIn.image)
            assertEquals(64f, standIn.width)
            awaitIdle(view)
            assertSame(store.latest(coarse)!!.image, view.frame(far).draws.single().image)

            // Zooming back in: the level-1 page stands in, drawn at its own doubled span.
            val closer = MapCamera(192.0, 192.0, 1.0, 64, 64)
            val unseen = MapPageKey(1, 1, 0)
            assertEquals(listOf(unseen), closer.visiblePages())
            val ancestor = view.frame(closer).draws.single()
            assertSame(store.latest(coarse)!!.image, ancestor.image)
            assertEquals(256f, ancestor.width)
            awaitIdle(view)
        }
    }

    private fun colorOf(store: MapPageStore, page: MapPageKey, x: Int, z: Int): Int =
        assertNotNull(store.latest(page)).colorAt(x, z)

    private fun awaitIdle(view: MapView) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (view.pendingCount() > 0) {
            check(System.nanoTime() < deadline) { "page builds did not finish" }
            Thread.sleep(5)
        }
    }

    private inline fun withStore(test: (MapPageStore) -> Unit) =
        TestBlocks.withDirectory("palimpsest-view-") { directory ->
            MapPageStore(
                    directory.resolve("map"),
                    TestBlocks.table(directory),
                    commitInterval = { Duration.ofSeconds(60) },
                    clock = { now },
                )
                .use(test)
        }
}
