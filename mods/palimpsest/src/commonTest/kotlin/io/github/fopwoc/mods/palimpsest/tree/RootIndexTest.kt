package io.github.fopwoc.mods.palimpsest.tree

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RootIndexTest {
    @Test
    fun appendedSnapshotsPreserveEveryHistoricalRootAcrossPageAndJumpBoundaries() {
        val roots = List(16385) { RootRecord(it * 3L + 1, MapTree.LEVELS, 0, 0, Ref.NULL) }
        var index = RootIndex(emptyList())
        val snapshots = ArrayList<Pair<Int, RootIndex>>()
        roots.forEachIndexed { at, root ->
            index = index.with(root)
            if (at % 256 == 0) snapshots += at to index
        }
        for (epoch in -1L..50000) {
            val at = ((epoch - 1) / 3).toInt()
            val expected =
                if (epoch < 1) RootRecord.EMPTY else roots[at.coerceAtMost(roots.lastIndex)]
            assertSame(expected, index.rootAt(epoch))
        }
        for ((at, snapshot) in snapshots) {
            assertEquals(at + 1, snapshot.size)
            assertSame(roots[at], snapshot.latest)
            assertSame(roots[at], snapshot.rootAt(Long.MAX_VALUE))
        }
        assertFailsWith<IllegalArgumentException> { index.with(roots.last()) }
        assertContentEquals(roots.map { it.epoch }.toLongArray(), index.epochs())
        index.epochs().fill(0)
        assertEquals(roots.last().epoch, index.latestEpoch)
    }

    @Test
    fun bulkLoadSortsOnceAndKeepsTheLastEqualEpochRootIncludingPageBoundaries() {
        val input =
            List(2049) { RootRecord(it / 300L, MapTree.LEVELS, 0, 0, Ref.NULL) }
                .shuffled(Random(11))
        val sorted = input.sortedBy { it.epoch }
        val index = RootIndex(input)
        for (epoch in -1L..9) {
            assertSame(
                sorted.lastOrNull { it.epoch <= epoch } ?: RootRecord.EMPTY,
                index.rootAt(epoch),
            )
        }
        assertSame(sorted.last(), index.latest)
        assertContentEquals(sorted.map { it.epoch }.toLongArray(), index.epochs())
        val appended = index.with(RootRecord(10, MapTree.LEVELS, 0, 0, Ref.NULL))
        assertSame(sorted.last(), index.rootAt(10))
        assertEquals(10, appended.latestEpoch)
        assertEquals(-1, RootIndex(emptyList()).latestEpoch)
        assertSame(RootRecord.EMPTY, RootIndex(emptyList()).rootAt(Long.MAX_VALUE))
    }
}
