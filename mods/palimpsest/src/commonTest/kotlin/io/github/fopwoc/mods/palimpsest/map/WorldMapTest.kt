package io.github.fopwoc.mods.palimpsest.map

import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldMapTest {
    @Test
    fun explicitMapSaveFlushesAcceptedLatestOnlyBatches() =
        TestBlocks.withDirectory("palimpsest-current-save-") { directory ->
            WorldMap(directory.resolve("y255"), TestBlocks.table(directory), historyEnabled = false)
                .use { map ->
                    map.observe(0, 0, TestBlocks.flat(1))
                    map.observe(0, 0, TestBlocks.flat(1))
                    map.store.commitAll()
                    assertEquals(0, map.store.writes.snapshot().flushes)
                    assertTrue(map.store.sealDue())
                    assertEquals(1, map.store.writes.snapshot().flushes)
                    assertEquals(false, map.store.sealDue())
                    map.observe(0, 0, TestBlocks.flat(2))
                    map.observe(0, 0, TestBlocks.flat(2))
                    map.store.commitAll()
                    assertEquals(1, map.store.writes.snapshot().flushes)
                    map.flush()
                    assertEquals(2, map.store.writes.snapshot().flushes)
                    map.flush()
                    assertEquals(2, map.store.writes.snapshot().flushes)
                }
            WorldMap(directory.resolve("y255"), TestBlocks.table(directory), historyEnabled = false)
                .use { map ->
                    assertEquals(
                        TestBlocks.shown(TestBlocks.BLUE),
                        assertNotNull(map.store.latest(MapPageKey.containingTile(0, 0, 0)))
                            .colorAt(0, 0),
                    )
                }
        }

    @Test
    fun observationsBecomeHistoryAndSurviveReopen() =
        TestBlocks.withDirectory("palimpsest-world-") { directory ->
            var now = 100_000L
            val page = MapPageKey.containingTile(5, 5, 0)
            val created: Long
            WorldMap(
                    directory.resolve("y255"),
                    TestBlocks.table(directory),
                    commitInterval = { Duration.ofSeconds(60) },
                    clock = { now },
                )
                .use { map ->
                    created = map.createdEpoch
                    assertEquals(now, created)
                    map.observe(5, 5, TestBlocks.flat(1))
                    map.observe(5, 5, TestBlocks.flat(1))
                    // Ticks commit on the background thread; flush commits here, deterministically.
                    map.flush()
                    now += 61_000
                    map.observe(5, 5, TestBlocks.flat(2))
                    map.observe(5, 5, TestBlocks.flat(2))
                    map.tick()
                    map.flush()
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
