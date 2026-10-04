package io.github.fopwoc.palimpsest.db

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SectionBlocksTest {
    @Test
    fun `packing round-trips at every width`() {
        val random = Random(1)
        for (kinds in listOf(1, 2, 3, 16, 17, 300, 4096)) {
            val blocks =
                IntArray(SectionBlocks.VOLUME) {
                    if (kinds == 4096) it else random.nextInt(kinds) * 31
                }
            val packed = SectionBlocks.of(blocks)
            assertContentEquals(blocks, packed.unpack())
            if (kinds == 1) assertEquals(0, packed.bits)
        }
    }
}
