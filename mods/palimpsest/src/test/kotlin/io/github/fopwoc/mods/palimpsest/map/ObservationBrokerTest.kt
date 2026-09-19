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
        val source = Any()
        val base = TileKey(0, 0)
        val quiet = TileKey(1, 0)
        assertTrue(broker.observe(base, TileRecord.solid(0, 1), source))
        assertTrue(broker.observe(quiet, TileRecord.solid(0, 2), source))
        assertEquals(0, broker.commitDue())
        assertFalse(broker.observe(base, TileRecord.solid(0, 1), source))
        assertFalse(broker.observe(quiet, TileRecord.solid(0, 2), source))
        assertEquals(2, broker.commitDue())
        assertEquals(setOf(base, quiet), commits.single().tiles.keys)
        assertEquals(1_000L, commits.single().epoch)
        assertTrue(commits.single().tiles.values.all { it.epoch == 1_000L })

        // Ten seconds of edits: the live view follows, history waits for the next commit.
        repeat(10) { step ->
            now += 1_000
            assertTrue(broker.observe(base, TileRecord.solid(0, 10 + step), source))
            assertEquals(0, broker.commitDue())
        }
        assertEquals(19, broker.latest(base)?.block(0))
        assertEquals(setOf(base), broker.pending().keys)
        assertFalse(broker.observe(base, TileRecord.solid(0, 19), source))
        assertEquals(0, broker.commitDue())
        now += 50_000
        assertEquals(1, broker.commitDue())
        assertTrue(broker.pending().isEmpty())
        assertEquals(listOf(base), commits.last().tiles.keys.toList())
        assertEquals(19, commits.last().tiles.getValue(base).block(0))

        // Observing the same facts again is a no-op, even at a new epoch.
        assertFalse(broker.observe(base, TileRecord.solid(5, 19), source))
        assertEquals(0, broker.pendingCount())
        // An edit that is undone before its commit never becomes history.
        assertTrue(broker.observe(base, TileRecord.solid(0, 99), source))
        assertTrue(broker.observe(base, TileRecord.solid(0, 19), source))
        now += 60_000
        assertEquals(0, broker.commitDue())
        assertEquals(2, commits.size)
        // A newly seen tile waits for the cadence too: one root per interval, not per tick.
        val first = TileKey(9, 9)
        val second = TileKey(9, 8)
        assertTrue(broker.observe(first, TileRecord.solid(0, 1), source))
        assertFalse(broker.observe(first, TileRecord.solid(0, 1), source))
        assertEquals(1, broker.commitDue())
        assertTrue(broker.observe(second, TileRecord.solid(0, 1), source))
        assertFalse(broker.observe(second, TileRecord.solid(0, 1), source))
        assertEquals(0, broker.commitDue())
        now += 60_000
        assertEquals(1, broker.commitDue())
    }

    @Test
    fun reloadedChunkMustConfirmFromItsNewSource() {
        val commits = ArrayList<ObservationBroker.Commit>()
        val broker = ObservationBroker({ commits += it }, { Duration.ZERO }) { 1L }
        val key = TileKey(0, 0)
        val beforeUnload = Any()
        val afterReload = Any()
        val view = TileRecord.solid(0, 7)

        assertTrue(broker.observe(key, view, beforeUnload))
        assertFalse(broker.observe(key, view, afterReload))
        assertEquals(0, broker.commitDue())
        assertFalse(broker.observe(key, view, afterReload))
        assertEquals(1, broker.commitDue())
    }

    @Test
    fun epochsStayStrictlyIncreasingAndCommitAllDoesNotBypassConfirmation() {
        val epochs = ArrayList<Long>()
        val broker = ObservationBroker({ epochs += it.epoch }, { Duration.ZERO }) { 5L }
        val key = TileKey(0, 0)
        repeat(3) { step ->
            broker.observe(key, TileRecord.solid(0, 1 + step))
            broker.observe(key, TileRecord.solid(0, 1 + step))
            assertEquals(1, broker.commitDue())
        }
        assertEquals(listOf(5L, 6L, 7L), epochs)
        broker.startAfter(100)
        broker.observe(key, TileRecord.solid(0, 50))
        broker.observe(key, TileRecord.solid(0, 50))
        assertEquals(1, broker.commitAll())
        assertEquals(101L, epochs.last())

        val guarded = ObservationBroker({}) { 5L }
        guarded.observe(key, TileRecord.solid(0, 1))
        assertEquals(0, guarded.commitAll())
    }
}
