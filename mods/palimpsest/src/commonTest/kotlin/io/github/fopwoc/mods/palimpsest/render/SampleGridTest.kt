package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.tree.Sample
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SampleGridTest {
    @Test
    fun packedFactsPreserveFieldBoundariesUnknownAirAndOwnedCopies() {
        val grid = SampleGrid(128)
        val random = Random(489)
        val boundaries = listOf(Sample.NONE, Sample(0, 0, 0, 0), Sample(65535, 255, 255, 65535))
        for (at in grid.samples.indices) {
            val sample =
                if (at < boundaries.size) boundaries[at]
                else
                    Sample(
                        random.nextInt(65536),
                        random.nextInt(256),
                        random.nextInt(256),
                        random.nextInt(65536),
                    )
            grid.set(at % grid.stride - 1, at / grid.stride - 1, sample)
            assertEquals(sample, grid.sample(at))
        }
        val copy = grid.copy()
        copy.set(0, 0, Sample.NONE)
        assertTrue(copy.sample(copy.index(0, 0)).isNone)
        assertTrue(!grid.sample(grid.index(0, 0)).isNone)
        copy.set(0, 0, 0, 0, 0, 0)
        assertEquals(Sample(0, 0, 0, 0), copy.sample(copy.index(0, 0)))
        assertEquals(129L * 129 * 8, grid.bytes)
        assertFailsWith<IllegalArgumentException> { grid.set(0, 0, 65536, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { grid.set(0, 0, 1, 256, 0, 0) }
        assertFailsWith<IllegalArgumentException> { grid.set(0, 0, 1, 0, 256, 0) }
        assertFailsWith<IllegalArgumentException> { grid.set(0, 0, 1, 0, 0, 65536) }
    }
}
