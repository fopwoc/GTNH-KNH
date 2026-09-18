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
    @Test
    fun blockLevelChurnBecomesOneLayerPerIntervalAndUnchangedTilesNone() {
        var now = 1_000_000L
        val committed = ArrayList<TileLayer>()
        val broker = ObservationBroker({ committed += it }, Duration.ofSeconds(60)) { now }
        val base = TileKey(0, 0)
        val quiet = TileKey(1, 0)
        val colors = ByteArray(TileLayer.PIXELS)
        broker.observe(base, colors)
        broker.observe(quiet, colors)
        assertNull(broker.latest(TileKey(9, 9)))
        assertEquals(2, broker.commitDue())
        assertEquals(2, committed.size)
        assertTrue(committed.all { it.epoch == now })

        // A minute of frantic building: 600 observations, each changing one more pixel.
        repeat(600) { step ->
            now += 100
            colors[step % TileLayer.PIXELS] = (step + 1).toByte()
            broker.observe(base, colors)
            broker.observe(quiet, ByteArray(TileLayer.PIXELS))
            broker.commitDue()
        }
        assertEquals(3, committed.size)
        assertEquals(base, committed.last().key)
        assertContentEquals(broker.latest(base), committed.last().colors)
        assertTrue(committed.last().epoch > committed.first().epoch)
        assertEquals(0, broker.pendingCount())

        // Live view is ahead of history until the next commit.
        colors[0] = 77
        broker.observe(base, colors)
        assertEquals(77, broker.latest(base)!![0])
        assertEquals(0, broker.commitDue())
        assertEquals(1, broker.commitAll())
        assertEquals(4, committed.size)
    }

    @Test
    fun epochsStayStrictlyIncreasingWhenTheClockDoesNot() {
        val committed = ArrayList<TileLayer>()
        val broker = ObservationBroker({ committed += it }, Duration.ZERO) { 5L }
        val key = TileKey(0, 0)
        repeat(3) { step ->
            broker.observe(key, ByteArray(TileLayer.PIXELS) { step.toByte() })
            assertEquals(1, broker.commitDue())
        }
        assertEquals(listOf(5L, 6L, 7L), committed.map(TileLayer::epoch))
    }
}
