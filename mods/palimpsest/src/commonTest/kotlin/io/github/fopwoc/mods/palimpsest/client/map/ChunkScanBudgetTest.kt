package io.github.fopwoc.mods.palimpsest.client.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkScanBudgetTest {
    @Test
    fun expensiveChunksYieldAndEachTickStillMakesProgress() {
        var now = 0L
        val budget = ChunkScanBudget(8, 2_000_000) { now }
        now = 10_000_000
        assertTrue(budget.take())
        assertFalse(budget.take())
        val next = ChunkScanBudget(2, 2_000_000) { now }
        assertTrue(next.take())
        now++
        assertTrue(next.take())
        assertFalse(next.take())
    }
}
