package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservationBrokerTest {
    @Test
    fun observationsMustBeConfirmedBeforeTheyCommit() {
        var now = 1_000L
        val commits = ArrayList<ObservationBroker.Commit>()
        val broker = ObservationBroker({ commits += it }, { Duration.ofSeconds(60) }) { now }
        val base = TileKey(0, 0)
        val quiet = TileKey(1, 0)
        assertTrue(broker.observe(base, TileRecord.solid(0, 1)))
        assertTrue(broker.observe(quiet, TileRecord.solid(0, 2)))
        assertEquals(0, broker.commitDue())
        now += 4_999
        assertFalse(broker.observe(base, TileRecord.solid(0, 1)))
        assertFalse(broker.observe(quiet, TileRecord.solid(0, 2)))
        assertEquals(0, broker.commitDue())
        now += 1
        assertFalse(broker.observe(base, TileRecord.solid(0, 1)))
        assertFalse(broker.observe(quiet, TileRecord.solid(0, 2)))
        assertEquals(2, broker.commitDue())
        assertEquals(setOf(base, quiet), commits.single().tiles.keys)
        assertEquals(6_000L, commits.single().epoch)
        assertTrue(commits.single().tiles.values.all { it.epoch == 6_000L })

        // Ten seconds of edits: the live view follows, history waits for the next commit.
        repeat(10) { step ->
            now += 1_000
            assertTrue(broker.observe(base, TileRecord.solid(0, 10 + step)))
            assertEquals(0, broker.commitDue())
        }
        assertEquals(19, broker.latest(base)?.block(0))
        assertEquals(setOf(base), broker.pending().keys)
        now += 50_000
        assertFalse(broker.observe(base, TileRecord.solid(0, 19)))
        assertEquals(1, broker.commitDue())
        assertTrue(broker.pending().isEmpty())
        assertEquals(listOf(base), commits.last().tiles.keys.toList())
        assertEquals(19, commits.last().tiles.getValue(base).block(0))

        // Observing the same facts again is a no-op, even at a new epoch.
        assertFalse(broker.observe(base, TileRecord.solid(5, 19)))
        assertEquals(0, broker.pendingCount())
        // An edit that is undone before its commit never becomes history.
        assertTrue(broker.observe(base, TileRecord.solid(0, 99)))
        now += 5_000
        assertTrue(broker.observe(base, TileRecord.solid(0, 19)))
        now += 60_000
        assertEquals(0, broker.commitDue())
        assertEquals(2, commits.size)
        // A newly seen tile waits for the cadence too: one root per interval, not per tick.
        val first = TileKey(9, 9)
        val second = TileKey(9, 8)
        assertTrue(broker.observe(first, TileRecord.solid(0, 1)))
        now += 5_000
        assertFalse(broker.observe(first, TileRecord.solid(0, 1)))
        assertEquals(1, broker.commitDue())
        assertTrue(broker.observe(second, TileRecord.solid(0, 1)))
        now += 5_000
        assertFalse(broker.observe(second, TileRecord.solid(0, 1)))
        assertEquals(0, broker.commitDue())
        now += 55_000
        assertEquals(1, broker.commitDue())
    }

    @Test
    fun epochsStayStrictlyIncreasingAndCommitAllDoesNotBypassConfirmation() {
        val epochs = ArrayList<Long>()
        val broker =
            ObservationBroker(
                { epochs += it.epoch },
                { Duration.ZERO },
                minimumStableAge = Duration.ZERO,
            ) {
                5L
            }
        val key = TileKey(0, 0)
        repeat(3) { step ->
            broker.observe(key, TileRecord.solid(0, 1 + step))
            assertEquals(1, broker.commitDue())
        }
        assertEquals(listOf(5L, 6L, 7L), epochs)
        broker.startAfter(100)
        broker.observe(key, TileRecord.solid(0, 50))
        assertEquals(1, broker.commitAll())
        assertEquals(101L, epochs.last())

        val guarded = ObservationBroker({}, minimumStableAge = Duration.ofSeconds(5)) { 5L }
        guarded.observe(key, TileRecord.solid(0, 1))
        assertEquals(0, guarded.commitAll())
    }
}
