package io.github.fopwoc.mods.palimpsest.map

import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldMapTest {
    @Test
    fun observationsBecomeHistoryAndSurviveReopen() =
        TestBlocks.withDirectory("palimpsest-world-") { directory ->
            var now = 100_000L
            val page = MapPageKey.containingTile(5, 5, 0)
            val created: Long
            WorldMap(
                    directory.resolve("y255"),
                    TestBlocks.table(directory),
                    commitInterval = Duration.ofSeconds(60),
                    clock = { now },
                )
                .use { map ->
                    created = map.createdEpoch
                    assertEquals(now, created)
                    map.observe(5, 5, TestBlocks.flat(1))
                    map.tick()
                    now += 61_000
                    map.observe(5, 5, TestBlocks.flat(2))
                    map.tick()
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(map.store.latest(page)).colorAt(80, 80),
                    )
                    map.flush()
                }
            assertTrue(
                Files.readString(directory.resolve("y255").resolve(WorldMap.CREATED_FILE)).trim() ==
                    created.toString()
            )
            WorldMap(directory.resolve("y255"), TestBlocks.table(directory), clock = { now }).use {
                reopened ->
                assertEquals(created, reopened.createdEpoch)
                assertEquals(
                    TestBlocks.shown(TestBlocks.BLUE),
                    assertNotNull(reopened.store.latest(page)).colorAt(80, 80),
                )
                assertEquals(
                    TestBlocks.shown(TestBlocks.RED),
                    assertNotNull(reopened.store.historical(page, now - 1)).colorAt(80, 80),
                )
            }
        }
}
