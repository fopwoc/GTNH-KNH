package io.github.fopwoc.mods.palimpsest.map

import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorldMapTest {
    @Test
    fun observeTickAndReopenRoundTrip() {
        val directory = Files.createTempDirectory("palimpsest-world-")
        val palette = intArrayOf(0, 0xFF0000, 0x0000FF) + IntArray(253)
        var now = 1_000_000L
        val page = MapPageKey.containingTile(5, 5, 0)
        try {
            WorldMap(
                    directory,
                    palette,
                    commitInterval = Duration.ofSeconds(60),
                    maintenanceEvery = Duration.ofSeconds(30),
                    clock = { now },
                )
                .use { map ->
                    map.observe(5, 5, ByteArray(WorldMap.TILE_PIXELS) { 1 })
                    map.tick()
                    now += 31_000
                    map.observe(5, 5, ByteArray(WorldMap.TILE_PIXELS) { 2 })
                    map.tick()
                    assertEquals(
                        0xFF0000FF.toInt(),
                        assertNotNull(map.store.latest(page)).colorAt(85, 85),
                    )
                    assertEquals(
                        0xFFFF0000.toInt(),
                        assertNotNull(map.store.historical(page, now)).colorAt(85, 85),
                    )
                    map.flush()
                    assertEquals(
                        0xFF0000FF.toInt(),
                        assertNotNull(map.store.historical(page, now)).colorAt(85, 85),
                    )
                }
            assertTrue(Files.isRegularFile(directory.resolve(".gitignore")))
            WorldMap(directory, palette).use { reopened ->
                assertEquals(
                    0xFF0000FF.toInt(),
                    assertNotNull(reopened.store.latest(page)).colorAt(85, 85),
                )
                assertEquals(
                    0xFFFF0000.toInt(),
                    assertNotNull(reopened.store.historical(page, 1_000_000L)).colorAt(85, 85),
                )
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
