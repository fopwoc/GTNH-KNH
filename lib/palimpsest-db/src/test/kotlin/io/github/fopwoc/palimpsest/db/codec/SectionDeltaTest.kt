package io.github.fopwoc.palimpsest.db.codec

import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectionDeltaTest {
    private val random = Random(9)
    private val base =
        IntArray(SectionBlocks.VOLUME) { at -> if (at shr 8 < 9) 1 + random.nextInt(3) else 0 }

    private fun roundTrip(after: IntArray): Int {
        val bytes = SectionDelta.encode(base, after)!!
        assertContentEquals(after, SectionDelta.apply(base, bytes))
        return bytes.size
    }

    @Test
    fun `small edits cost a few bytes`() {
        assertTrue(roundTrip(base.copyOf().also { it[1234] = 77 }) < 16)
        assertTrue(
            roundTrip(
                base.copyOf().also { for (x in 0..15) it[SectionBlocks.index(x, 3, 3)] = 0 }
            ) < 24
        )
    }

    @Test
    fun `many new ids survive`() {
        roundTrip(
            base.copyOf().also {
                for (i in 0 until 1500) it[random.nextInt(4096)] = 100 + random.nextInt(200)
            }
        )
    }

    @Test
    fun `no change or a rewrite is not a delta`() {
        assertNull(SectionDelta.encode(base, base.copyOf()))
        assertNull(SectionDelta.encode(base, IntArray(SectionBlocks.VOLUME) { 9 }))
    }
}
