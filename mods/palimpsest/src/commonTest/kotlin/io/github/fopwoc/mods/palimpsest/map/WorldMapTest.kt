package io.github.fopwoc.mods.palimpsest.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorldMapTest {
    @Test
    fun stagedChunksBecomeHistoryAndSurviveReopen() =
        TestBlocks.withDirectory("palimpsest-world-") { directory ->
            val blocks = TestBlocks.table(directory)
            val page = MapPageKey.containingTile(5, 5, 0)
            TestHistory(directory, blocks).use { history ->
                WorldMap(blocks, history.dimension, worldTime = { 100 }).use { map ->
                    history.stage(5, 5, "red")
                    map.flush()
                    // The game is paused: world time stands still, but the next moment is later.
                    history.stage(5, 5, "blue")
                }
            }
            TestHistory(directory, blocks).use { history ->
                WorldMap(blocks, history.dimension, worldTime = { 100 }).use { map ->
                    history.dimension.dimension.ready.get()
                    assertEquals(
                        listOf(100L, 101L),
                        history.dimension.dimension.timeline().map { it.tick.value },
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(map.store.latest(page)).colorAt(80, 80),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        assertNotNull(map.store.historical(page, 100)).colorAt(80, 80),
                    )
                }
            }
        }
}
