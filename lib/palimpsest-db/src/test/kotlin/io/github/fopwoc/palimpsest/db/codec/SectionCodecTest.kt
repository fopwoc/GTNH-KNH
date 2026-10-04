package io.github.fopwoc.palimpsest.db.codec

import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SectionCodecTest {
    private fun roundTrip(blocks: IntArray): Int {
        val sink = ByteSink()
        SectionCodec.encode(blocks, sink)
        assertContentEquals(blocks, SectionCodec.decode(ByteSource(sink.toByteArray())))
        return sink.size
    }

    @Test
    fun `layered terrain is small`() {
        val blocks =
            IntArray(SectionBlocks.VOLUME) { at ->
                if (at shr 8 < 10) 1 + (at % 7 == 0).compareTo(false) else 0
            }
        assertTrue(roundTrip(blocks) < 400)
    }

    @Test
    fun `single block sections hold only the palette`() {
        assertTrue(roundTrip(IntArray(SectionBlocks.VOLUME) { 42 }) <= 2)
    }

    @Test
    fun `noise and huge palettes survive`() {
        val random = Random(7)
        roundTrip(IntArray(SectionBlocks.VOLUME) { random.nextInt(20) })
        roundTrip(IntArray(SectionBlocks.VOLUME) { random.nextInt(3000) })
        roundTrip(IntArray(SectionBlocks.VOLUME) { it })
    }

    @Test
    fun `biomes survive`() {
        val random = Random(3)
        for (kinds in listOf(1, 2, 5, 40)) {
            val biomes = IntArray(256) { if (it % 16 < 8) random.nextInt(kinds) else 0 }
            val sink = ByteSink()
            BiomeCodec.encode(biomes, sink)
            assertContentEquals(biomes, BiomeCodec.decode(ByteSource(sink.toByteArray())))
        }
    }
}
