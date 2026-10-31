package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldMapTest {
    @Test
    fun stagedChunksBecomeHistoryAndSurviveReopen() =
        TestBlocks.withDirectory("palimpsest-world-") { directory ->
            val blocks = TestBlocks.table(directory)
            val tile = TileKey(5, 5)
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
                        TestBlocks.colorOf(map.store.shader, map.store.latestTile(tile)),
                    )
                    assertEquals(
                        TestBlocks.shown(TestBlocks.RED),
                        TestBlocks.colorOf(map.store.shader, history.tiles().tile(tile, 100)),
                    )
                }
            }
        }
}
