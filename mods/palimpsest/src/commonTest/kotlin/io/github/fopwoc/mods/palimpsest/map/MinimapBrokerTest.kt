package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinimapBrokerTest {
    private fun broker(directory: java.nio.file.Path) =
        MinimapBroker(TestBlocks.table(directory), { 0xFFFFFF }, { 0xFFFFFF }, { 0xFFFFFF })

    private fun MinimapBroker.shown(tile: TileKey): TileRecord? = tiles(tile.x, tile.z, 1, 1)[0]

    @Test
    fun tilesCanBeBorrowedOnlyAcrossProvenEmptyIntervals() =
        TestBlocks.withDirectory("palimpsest-minimap-reuse-") { directory ->
            val broker = broker(directory)
            val tile = TileKey(0, 0)

            broker.atHeight(31)
            broker.observe(31, tile, TestBlocks.flat(1))
            broker.atHeight(47)
            assertTrue(broker.reuseVisible(47, tile) { from, to -> from == 32 && to == 47 })
            broker.publish()
            assertEquals(TestBlocks.flat(1), broker.shown(tile))
            assertFalse(broker.reuseVisible(47, tile) { from, to -> from == 32 && to == 47 })

            broker.atHeight(15)
            assertFalse(broker.reuseVisible(15, tile) { from, to -> from == 32 && to == 47 })
            broker.publish()
            assertNull(broker.shown(tile))
        }

    @Test
    fun recentHeightsAreReusedButLateObservationsCannotEnterTheActiveSlice() =
        TestBlocks.withDirectory("palimpsest-minimap-") { directory ->
            val broker = broker(directory)
            val tile = TileKey(0, 0)

            assertFalse(broker.atHeight(64))
            broker.observe(64, tile, TestBlocks.flat(1))
            assertEquals(TestBlocks.flat(1), broker.shown(tile))

            assertFalse(broker.atHeight(65))
            broker.observe(64, tile, TestBlocks.flat(2))
            assertEquals(TestBlocks.flat(1), broker.shown(tile))
            broker.observe(65, tile, TestBlocks.flat(2))
            broker.publish()
            assertEquals(TestBlocks.flat(2), broker.shown(tile))
            assertTrue(broker.atHeight(64))
            broker.publish()
            assertEquals(TestBlocks.flat(1), broker.shown(tile))
            broker.atHeight(65)
            broker.publish()
            assertEquals(TestBlocks.flat(2), broker.shown(tile))
            for (height in 66..68) {
                broker.atHeight(height)
                broker.publish()
            }
            assertFalse(broker.atHeight(65))
            broker.publish()
            assertNull(broker.shown(tile))
        }

    @Test
    fun changingLayersWaitsForTheVisibleRegionAndBatchesNotifications() =
        TestBlocks.withDirectory("palimpsest-minimap-ready-") { directory ->
            val broker = broker(directory)
            val left = TileKey(0, 0)
            val right = TileKey(1, 0)
            val heard = mutableListOf<Collection<TileKey>>()
            broker.addChangeListener { heard += it }
            broker.atHeight(255)
            broker.observe(255, left, TestBlocks.flat(1))
            broker.observe(255, right, TestBlocks.flat(1))
            assertEquals(0, heard.size)
            broker.publish()
            assertEquals(listOf(setOf(left, right)), heard.map { it.toSet() })
            broker.request(GroundArea(1.0, 1.0, 31.0, 15.0))
            broker.atHeight(79)
            val missing = broker.prepare(0, 0, 2)
            broker.observe(79, left, TestBlocks.flat(2))
            broker.publish()
            // The new height waits for every tile in view before it shows.
            assertEquals(TestBlocks.flat(1), broker.shown(left))
            assertEquals(1, heard.size)
            for (key in missing) {
                if (key == right) broker.observe(79, key, TestBlocks.flat(2))
                else broker.surveyed(79, key)
            }
            broker.publish()
            assertEquals(2, heard.size)
            assertEquals(TestBlocks.flat(2), broker.shown(left))
            assertEquals(TestBlocks.flat(2), broker.shown(right))
        }
}
