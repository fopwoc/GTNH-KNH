package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapViewTest {
    private val palette = intArrayOf(0xFF0000, 0x0000FF, 0x00FF00) + IntArray(253)
    private var now = 50_000L

    @Test
    fun framesNeverBlockAndPagesArriveThenFollowObservationsAndTime() = withStore { store ->
        val changes = AtomicInteger()
        MapView(store, parallelism = 2, onChanged = { changes.incrementAndGet() }).use { view ->
            val tile = TileKey(1, 1)
            store.observe(tile, ByteArray(TileLayer.PIXELS))
            val camera = MapCamera(64.0, 64.0, 1.0, 64, 64)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            assertEquals(listOf(page), camera.visiblePages())

            assertTrue(view.frame(camera).draws.isEmpty())
            awaitIdle(view)
            val first = view.frame(camera).draws.single()
            assertEquals(0xFFFF0000.toInt(), colorOf(store, page, 16, 16))
            assertSame(first.image, view.frame(camera).draws.single().image)
            assertTrue(changes.get() >= 1)

            // A new observation keeps the old page on screen while the replacement builds.
            store.observe(tile, ByteArray(TileLayer.PIXELS) { 1 })
            assertSame(first.image, view.frame(camera).draws.single().image)
            awaitIdle(view)
            // Observing the same colors again changes nothing and schedules nothing.
            val settled = view.frame(camera).draws.single().image
            store.observe(tile, ByteArray(TileLayer.PIXELS) { 1 })
            assertEquals(0, view.pendingCount())
            assertSame(settled, view.frame(camera).draws.single().image)
            assertEquals(0, view.pendingCount())
            val second = view.frame(camera).draws.single()
            assertTrue(first.image !== second.image)
            assertEquals(0xFF0000FF.toInt(), colorOf(store, page, 16, 16))

            // History as of a moment before the commit shows the committed colors.
            assertEquals(1, store.commitDue())
            now += 60_000
            store.observe(tile, ByteArray(TileLayer.PIXELS) { 2 })
            assertEquals(1, store.commitDue())
            // Scrubbing keeps the live page on screen until the historical one is built.
            val live = view.frame(camera).draws.single().image
            assertSame(live, view.frame(camera, MapTime.At(now - 1)).draws.single().image)
            awaitIdle(view)
            val historical = assertNotNull(store.historical(page, now - 1))
            assertEquals(0xFF0000FF.toInt(), historical.colorAt(16, 16))
            val scrubbed = view.frame(camera, MapTime.At(now - 1)).draws.single().image
            assertTrue(scrubbed !== live)
            assertSame(historical.image, scrubbed)
            // Back to live: the historical page stays until the live one is rebuilt.
            assertSame(scrubbed, view.frame(camera).draws.single().image)
            awaitIdle(view)
            assertEquals(0xFF00FF00.toInt(), colorOf(store, page, 16, 16))
            assertTrue(view.frame(camera).draws.single().image !== scrubbed)
        }
    }

    @Test
    fun pagesScrolledOutOfViewAreNotBuilt() = withStore { store ->
        for (x in 0 until 64) store.observe(TileKey(x * 8, 0), ByteArray(TileLayer.PIXELS))
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

    private fun colorOf(store: MapPageStore, page: MapPageKey, x: Int, z: Int): Int =
        assertNotNull(store.latest(page)).colorAt(x, z)

    private fun awaitIdle(view: MapView) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (view.pendingCount() > 0) {
            check(System.nanoTime() < deadline) { "page builds did not finish" }
            Thread.sleep(5)
        }
    }

    private inline fun withStore(test: (MapPageStore) -> Unit) {
        val directory = Files.createTempDirectory("palimpsest-view-")
        try {
            MapPageStore(
                    directory,
                    palette,
                    commitInterval = Duration.ofSeconds(60),
                    clock = { now },
                )
                .use(test)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
