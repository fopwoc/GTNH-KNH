package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservationBrokerTest {
    private fun plane(value: Int) = ByteArray(TileLayer.PIXELS) { value.toByte() }

    @Test
    fun blockLevelChurnBecomesOneLayerPerIntervalAndUnchangedTilesNone() {
        var now = 1_000_000L
        val commits = ArrayList<ObservationBroker.Commit>()
        val broker = ObservationBroker(2, { commits += it }, Duration.ofSeconds(60)) { now }
        val base = TileKey(0, 0)
        val quiet = TileKey(1, 0)
        val colors = ByteArray(TileLayer.PIXELS)
        val biomes = plane(4)
        broker.observe(base, arrayOf(colors, biomes))
        broker.observe(quiet, arrayOf(colors, biomes))
        assertNull(broker.latest(TileKey(9, 9), 0))
        assertEquals(2, broker.commitDue())
        assertEquals(1, commits.size)
        assertEquals(2, commits.single().layers.size)
        assertEquals(2, commits.single().layers[0].size)
        assertEquals(2, commits.single().layers[1].size)
        assertTrue(commits.single().layers.flatten().all { it.epoch == now })

        // A minute of frantic building: 600 observations, each changing one more pixel.
        repeat(600) { step ->
            now += 100
            colors[step % TileLayer.PIXELS] = (step + 1).toByte()
            broker.observe(base, arrayOf(colors, biomes))
            broker.observe(quiet, arrayOf(ByteArray(TileLayer.PIXELS), biomes))
            broker.commitDue()
        }
        assertEquals(2, commits.size)
        val second = commits.last()
        assertEquals(base, second.layers[0].single().key)
        assertContentEquals(broker.latest(base, 0), second.layers[0].single().colors)
        // The biome plane is committed alongside; the store's diff will drop it as unchanged.
        assertContentEquals(biomes, second.layers[1].single().colors)
        assertTrue(second.epoch > commits.first().epoch)
        assertEquals(0, broker.pendingCount())

        // Live view is ahead of history until the next commit.
        colors[0] = 77
        broker.observe(base, arrayOf(colors, biomes))
        assertEquals(77, broker.latest(base, 0)!![0])
        assertEquals(0, broker.commitDue())
        assertEquals(1, broker.commitAll())
        assertEquals(3, commits.size)
    }

    @Test
    fun epochsStayStrictlyIncreasingWhenTheClockDoesNot() {
        val epochs = ArrayList<Long>()
        val broker = ObservationBroker(1, { epochs += it.epoch }, Duration.ZERO) { 5L }
        val key = TileKey(0, 0)
        repeat(3) { step ->
            broker.observe(key, arrayOf(plane(step)))
            assertEquals(1, broker.commitDue())
        }
        assertEquals(listOf(5L, 6L, 7L), epochs)
    }
}
