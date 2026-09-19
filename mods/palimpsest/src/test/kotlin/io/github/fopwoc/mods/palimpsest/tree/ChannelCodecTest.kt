package io.github.fopwoc.mods.palimpsest.tree

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelCodecTest {
    private fun roundTrip(values: IntArray, width: Int): Int {
        val sink = ByteSink()
        ChannelCodec.encode(sink, values, width)
        val bytes = sink.toByteArray()
        val source = ByteSource(bytes)
        assertContentEquals(values, ChannelCodec.decode(source, values.size, width))
        assertEquals(0, source.remaining)
        return bytes.size
    }

    @Test
    fun solidCostsModeAndValue() {
        assertEquals(3, roundTrip(IntArray(256) { 0x1234 }, 2))
        assertEquals(2, roundTrip(IntArray(7) { 9 }, 1))
    }

    @Test
    fun paletteScalesWithDistinctCount() {
        val two = roundTrip(IntArray(256) { it % 2 }, 2)
        val four = roundTrip(IntArray(256) { it % 4 }, 2)
        val eight = roundTrip(IntArray(256) { (it * 7) % 8 }, 2)
        assertTrue(two <= 1 + 1 + 4 + 32, "two values: $two")
        assertTrue(four <= 1 + 1 + 8 + 64, "four values: $four")
        assertTrue(eight <= 1 + 1 + 16 + 96, "eight values: $eight")
        assertTrue(two < four && four < eight)
    }

    @Test
    fun smoothHeightsPredictToAFewBits() {
        val gentle = IntArray(256) { 64 + (it % 16) / 4 + (it / 16) / 5 }
        val size = roundTrip(gentle, 1)
        assertTrue(size <= 1 + 1 + 1 + 64, "gentle terrain: $size")
        val flat = IntArray(256) { 70 }
        assertEquals(2, roundTrip(flat, 1))
    }

    @Test
    fun noiseFallsBackToRawOrPaletteWithoutGrowingPastRaw() {
        val random = Random(7)
        val noise = IntArray(256) { random.nextInt(65536) }
        assertTrue(roundTrip(noise, 2) <= 1 + 512)
        val bytes = IntArray(256) { random.nextInt(256) }
        assertTrue(roundTrip(bytes, 1) <= 1 + 256)
    }

    @Test
    fun partialGridsUseSolidOrPalette() {
        assertEquals(1 + 2, roundTrip(intArrayOf(5), 2))
        assertTrue(roundTrip(intArrayOf(5, 6, 5, 6, 7), 2) <= 1 + 1 + 6 + 2)
    }

    @Test
    fun randomRoundTrips() {
        val random = Random(42)
        repeat(200) {
            val count = if (random.nextBoolean()) 256 else 1 + random.nextInt(255)
            val width = 1 + random.nextInt(2)
            val distinct = 1 + random.nextInt(if (random.nextBoolean()) 3 else 40)
            val alphabet = IntArray(distinct) { random.nextInt(1 shl (width * 8)) }
            roundTrip(IntArray(count) { alphabet[random.nextInt(distinct)] }, width)
        }
    }
}
