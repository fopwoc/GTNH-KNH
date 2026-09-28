package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinimapBrokerTest {
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
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(page)).colorAt(0, 0),
            )
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(distant)).colorAt(0, 0),
            )

            assertFalse(broker.atHeight(65))
            broker.observe(64, tile, TestBlocks.flat(2))
            assertNull(broker.latest(page))
            broker.observe(65, tile, TestBlocks.flat(2))
            assertEquals(
                TestBlocks.shown(TestBlocks.BLUE),
                assertNotNull(broker.latest(page)).colorAt(0, 0),
            )
            assertTrue(broker.atHeight(64))
            assertEquals(
                TestBlocks.shown(TestBlocks.RED),
                assertNotNull(broker.latest(page)).colorAt(0, 0),
            )
            broker.atHeight(66)
            broker.atHeight(67)
            assertFalse(broker.atHeight(65))
            assertNull(broker.latest(page))
            assertNull(broker.historical(page, 1))
        }
}
