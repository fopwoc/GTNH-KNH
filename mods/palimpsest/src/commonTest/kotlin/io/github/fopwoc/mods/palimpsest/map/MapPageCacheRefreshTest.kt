package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MapPageCacheRefreshTest {
    @Test
    fun invalidationDuringPartialBuildCannotPublishStaleFactsOrMutateAnOlderRaster() {
        val source = Source()
        val a = TileKey(0, 0)
        val b = TileKey(1, 0)
        val key = MapPageKey(0, 0, 0)
        source.records[a] = TileRecord.solid(1, 1, 60)
        source.records[b] = TileRecord.solid(1, 2, 60)
        val builder = builder(source)
        val budget = PageSampleBudget(129L * 129 * 8)
        val cache = MapPageCache(builder, null, sampleBudget = budget)
        val competing = MapPageCache(builder, null, sampleBudget = budget)
        val original = assertNotNull(cache.latest(key))
        val pixels = original.copyPixels()
        source.records[a] = TileRecord.solid(2, 3, 70)
        cache.invalidateTiles(listOf(a), Long.MAX_VALUE)
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val once = AtomicBoolean()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending =
                executor.submit<MapPageRaster?> {
                    cache.latest(key) {
                        if (once.compareAndSet(false, true)) {
                            entered.countDown()
                            check(resume.await(10, TimeUnit.SECONDS))
                        }
                    }
                }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertNotNull(competing.latest(key))
            assertTrue(budget.retainedBytes <= budget.maxBytes)
            source.records[b] = TileRecord.solid(3, 4, 90)
            cache.invalidateTiles(listOf(b), Long.MAX_VALUE)
            resume.countDown()
            pending.get(10, TimeUnit.SECONDS)
            assertEquals(0, cache.cachedLatestPages())
            val fresh = assertNotNull(cache.latest(key))
            assertContentEquals(
                assertNotNull(builder.build(key, Long.MAX_VALUE)).copyPixels(),
                fresh.copyPixels(),
            )
            assertContentEquals(pixels, original.copyPixels())
            assertSame(fresh, cache.latest(key))
        } finally {
            resume.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun dirtySnapshotsShareTheLruBoundAndNoOpRefreshesKeepImageIdentity() {
        val source = Source()
        for (x in 0..2) source.records[TileKey(x * 8, 0)] = TileRecord.solid(1, 1, 60)
        val cache = MapPageCache(builder(source), null, maxLatestPages = 2)
        val a = MapPageKey(0, 0, 0)
        val old = assertNotNull(cache.latest(a))
        cache.invalidateTiles(listOf(TileKey(0, 0)), Long.MAX_VALUE)
        assertSame(old, cache.latest(a))
        cache.invalidateTiles(listOf(TileKey(0, 0)), Long.MAX_VALUE)
        cache.latest(MapPageKey(1, 0, 0))
        cache.latest(MapPageKey(2, 0, 0))
        assertEquals(2, cache.cachedLatestPages())
        source.reads.set(0)
        cache.latest(a)
        assertEquals(81, source.reads.get())
        assertEquals(2, cache.cachedLatestPages())
    }

    @Test
    fun failedRefreshRemainsDirtyAndADenseInvalidationForcesACompleteTileWindow() {
        val source = Source()
        val tiles = (0 until 64).map { TileKey(it % 8, it / 8) }
        tiles.forEach { source.records[it] = TileRecord.solid(1, 1, 60) }
        val cache = MapPageCache(builder(source), null)
        val key = MapPageKey(0, 0, 0)
        cache.latest(key)
        cache.invalidateTiles(tiles, Long.MAX_VALUE)
        kotlin.test.assertFailsWith<IllegalStateException> {
            cache.latest(key) { error("cancelled") }
        }
        assertEquals(0, cache.cachedLatestPages())
        source.reads.set(0)
        assertNotNull(cache.latest(key))
        assertEquals(81, source.reads.get())
    }

    @Test
    fun invalidationDoesNotPromoteAnOffscreenPageAheadOfTheMostRecentlyViewedPage() {
        val source = Source()
        for (x in 0..2) source.records[TileKey(x * 8, 0)] = TileRecord.solid(1, 1, 60)
        val cache = MapPageCache(builder(source), null, maxLatestPages = 2)
        val a = MapPageKey(0, 0, 0)
        cache.latest(a)
        cache.latest(MapPageKey(1, 0, 0))
        val viewed = assertNotNull(cache.latest(a))
        cache.invalidateTiles(listOf(TileKey(8, 0)), Long.MAX_VALUE)
        cache.latest(MapPageKey(2, 0, 0))
        source.reads.set(0)
        assertSame(viewed, cache.latest(a))
        assertEquals(0, source.reads.get())
    }

    @Test
    fun sharedByteBudgetEvictsFactsWithoutDroppingRastersAndReleasesReplacedEntries() {
        val source = Source()
        source.records[TileKey(0, 0)] = TileRecord.solid(1, 1, 60)
        val bytes = 129L * 129 * 8
        val budget = PageSampleBudget(bytes)
        val a = MapPageCache(builder(source), null, sampleBudget = budget)
        val b = MapPageCache(builder(source), null, sampleBudget = budget)
        val key = MapPageKey(0, 0, 0)
        val oldA = assertNotNull(a.latest(key))
        val oldB = assertNotNull(b.latest(key))
        assertEquals(bytes, budget.retainedBytes)
        source.reads.set(0)
        assertSame(oldA, a.latest(key))
        assertEquals(0, source.reads.get())
        a.invalidateTiles(listOf(TileKey(0, 0)), Long.MAX_VALUE)
        assertSame(oldA, a.latest(key))
        assertEquals(81, source.reads.get())
        assertEquals(bytes, budget.retainedBytes)
        source.reads.set(0)
        assertSame(oldB, b.latest(key))
        source.records[TileKey(0, 0)] = TileRecord.solid(2, 9, 70)
        b.invalidateTiles(listOf(TileKey(0, 0)), Long.MAX_VALUE)
        val changed = assertNotNull(b.latest(key))
        assertEquals(81, source.reads.get())
        assertContentEquals(
            assertNotNull(builder(source).build(key, Long.MAX_VALUE)).copyPixels(),
            changed.copyPixels(),
        )
        assertEquals(bytes, budget.retainedBytes)
        a.clear()
        assertEquals(bytes, budget.retainedBytes)
        b.clear()
        assertEquals(0, budget.retainedBytes)
        val zero = PageSampleBudget(0)
        val cache = MapPageCache(builder(source), null, sampleBudget = zero)
        assertNotNull(cache.latest(key))
        assertEquals(0, zero.retainedBytes)
    }

    private fun builder(source: Source) =
        PageBuilder(source, TerrainShader({ it * 7919 and 0xFFFFFF }, { 0 }, { 0xFFFFFF }))

    private class Source : TileSource {
        val records = ConcurrentHashMap<TileKey, TileRecord>()
        val reads = AtomicInteger()
        override val latestEpoch = 1L

        override fun tile(key: TileKey, epoch: Long): TileRecord? {
            reads.incrementAndGet()
            return records[key]
        }

        override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long) =
            LongArray(side * side) { Sample.NONE.packed }
    }
}
