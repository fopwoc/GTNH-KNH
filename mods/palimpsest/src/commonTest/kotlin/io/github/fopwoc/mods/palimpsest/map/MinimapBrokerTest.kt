package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MinimapBrokerTest {
    @Test
    fun tilesCanBeBorrowedOnlyAcrossProvenEmptyIntervals() =
        TestBlocks.withDirectory("palimpsest-minimap-reuse-") { directory ->
            val broker =
                MinimapBroker(TestBlocks.table(directory), { 0xFFFFFF }, { 0xFFFFFF }, { 0xFFFFFF })
            val tile = TileKey(0, 0)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)

            broker.atHeight(31)
            broker.observe(31, tile, TestBlocks.flat(1))
            broker.atHeight(47)
            assertTrue(broker.reuseVisible(47, tile) { from, to -> from == 32 && to == 47 })
            broker.publish()
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(page)).colorAt(0, 0),
            )
            assertFalse(broker.reuseVisible(47, tile) { from, to -> from == 32 && to == 47 })

            broker.atHeight(15)
            assertFalse(broker.reuseVisible(15, tile) { from, to -> from == 32 && to == 47 })
            broker.publish()
            assertNull(broker.latest(page))
        }

    @Test
    fun recentHeightsAreReusedButLateObservationsCannotEnterTheActiveSlice() =
        TestBlocks.withDirectory("palimpsest-minimap-") { directory ->
            val broker =
                MinimapBroker(TestBlocks.table(directory), { 0xFFFFFF }, { 0xFFFFFF }, { 0xFFFFFF })
            val tile = TileKey(0, 0)
            val page = MapPageKey.containingTile(tile.x, tile.z, 0)
            val distant = MapPageKey.containingTile(tile.x, tile.z, 4)

            assertFalse(broker.atHeight(64))
            broker.observe(64, tile, TestBlocks.flat(1))
            val firstPage = assertNotNull(broker.latest(page))
            assertEquals(TestBlocks.shown(TestBlocks.RED), firstPage.colorAt(0, 0))
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(distant)).colorAt(0, 0),
            )

            assertFalse(broker.atHeight(65))
            broker.observe(64, tile, TestBlocks.flat(2))
            assertSame(firstPage, broker.latest(page))
            broker.observe(65, tile, TestBlocks.flat(2))
            broker.publish()
            val secondPage = assertNotNull(broker.latest(page))
            assertEquals(
                TestBlocks.shown(TestBlocks.BLUE),
                secondPage.colorAt(0, 0),
            )
            assertTrue(broker.atHeight(64))
            broker.publish()
            assertSame(firstPage, broker.latest(page))
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(page)).colorAt(0, 0),
            )
            broker.observe(64, tile, TestBlocks.flat(2))
            assertNotSame(firstPage, broker.latest(page))
            broker.atHeight(65)
            broker.publish()
            assertSame(secondPage, broker.latest(page))
            broker.atHeight(66)
            broker.publish()
            broker.atHeight(67)
            broker.publish()
            broker.atHeight(68)
            broker.publish()
            assertFalse(broker.atHeight(65))
            broker.publish()
            assertNull(broker.latest(page))
            assertNull(broker.historical(page, 1))
        }

    @Test
    fun changingLayersWaitsForTheVisibleRegionAndBatchesNotifications() =
        TestBlocks.withDirectory("palimpsest-minimap-ready-") { directory ->
            val broker =
                MinimapBroker(TestBlocks.table(directory), { 0xFFFFFF }, { 0xFFFFFF }, { 0xFFFFFF })
            val left = TileKey(0, 0)
            val right = TileKey(1, 0)
            val page = MapPageKey.containingTile(0, 0, 0)
            var notifications = 0
            broker.addInvalidationListener { notifications++ }
            broker.atHeight(255)
            broker.observe(255, left, TestBlocks.flat(1))
            broker.observe(255, right, TestBlocks.flat(1))
            assertEquals(0, notifications)
            broker.publish()
            assertEquals(1, notifications)
            val before = assertNotNull(broker.latest(page))
            broker.request(MapCamera(16.0, 8.0, 1.0, 30, 14))
            broker.atHeight(79)
            val missing = broker.prepare(0, 0, 2)
            broker.observe(79, left, TestBlocks.flat(2))
            broker.publish()
            assertSame(before, broker.latest(page))
            assertEquals(0L, broker.revision)
            for (key in missing) {
                if (key == right) broker.observe(79, key, TestBlocks.flat(2))
                else broker.surveyed(79, key)
            }
            broker.publish()
            assertEquals(1L, broker.revision)
            assertEquals(2, notifications)
            val after = assertNotNull(broker.latest(page))
            assertEquals(TestBlocks.shown(TestBlocks.BLUE), after.colorAt(0, 0))
            assertEquals(TestBlocks.shown(TestBlocks.BLUE), after.colorAt(16, 0))
        }

    @Test
    fun reliefBordersRefreshWithoutDroppingUnrelatedPages() =
        TestBlocks.withDirectory("palimpsest-minimap-border-") { directory ->
            val broker =
                MinimapBroker(TestBlocks.table(directory), { 0xFFFFFF }, { 0xFFFFFF }, { 0xFFFFFF })
            val west = TileKey(7, 2)
            val east = TileKey(8, 2)
            val eastPage = MapPageKey.containingTile(east.x, east.z, 0)
            val untouchedPage = MapPageKey.containingTile(32, 2, 0)
            broker.atHeight(255)
            for (tile in listOf(west, east, TileKey(32, 2))) broker.observe(
                255,
                tile,
                TestBlocks.flat(1),
            )
            broker.publish()
            val before = assertNotNull(broker.latest(eastPage)).colorAt(0, 32)
            val untouched = assertNotNull(broker.latest(untouchedPage))
            var affected = emptyList<MapPageKey>()
            broker.addInvalidationListener { affected = it.toList() }
            broker.observe(255, west, TileRecord.solid(0, 1, 67, biome = 1))
            broker.publish()
            assertTrue(eastPage in affected)
            assertTrue(untouchedPage !in affected)
            assertTrue(before != assertNotNull(broker.latest(eastPage)).colorAt(0, 32))
            assertSame(untouched, broker.latest(untouchedPage))
        }
}
