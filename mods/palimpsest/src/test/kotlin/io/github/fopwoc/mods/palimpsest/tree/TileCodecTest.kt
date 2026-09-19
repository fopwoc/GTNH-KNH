package io.github.fopwoc.mods.palimpsest.tree

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileCodecTest {
    private fun full(record: TileRecord, previous: Ref = Ref.NULL): ByteArray =
        ByteSink().also { TileCodec.encodeFull(it, record, previous) }.toByteArray()

    @Test
    fun solidTileIsAFewBytes() {
        val record = TileRecord.solid(1_700_000_000_000L, block = 3, height = 64, depth = 0, biome = 1)
        val bytes = full(record)
        assertTrue(bytes.size <= 1 + 6 + 1 + (3 + 2 + 2 + 3), "solid tile: ${bytes.size}")
        val decoded = TileCodec.decode(ByteSource(bytes))
        assertTrue(decoded.isFull)
        assertEquals(record, decoded.record)
        assertTrue(decoded.base.isNull)
    }

    @Test
    fun gentleTerrainStaysSmall() {
        val record =
            TileRecord.build(
                epoch = 1_700_000_000_000L,
                block = { position -> if (position % 16 < 5) 2 else if (position / 16 > 12) 7 else 1 },
                height = { position -> 60 + (position % 16) / 3 + (position / 16) / 4 },
                depth = { 0 },
                biome = { position -> if (position % 16 < 8) 1 else 6 },
            )
        val bytes = full(record)
        // Bit-packed: 2-bit block indices, 2-bit height residuals, 1-bit biome; entropy coding tightens this.
        assertTrue(bytes.size <= 200, "gentle terrain: ${bytes.size}")
        assertEquals(record, TileCodec.decode(ByteSource(bytes)).record)
    }

    @Test
    fun deltaCarriesOnlyChangedPixels() {
        val epoch = 1_700_000_000_000L
        val base = TileRecord.solid(epoch, block = 1, height = 64)
        val edited = base.with(epoch + 60_000, intArrayOf(37), arrayOf(intArrayOf(9), intArrayOf(65), intArrayOf(0), intArrayOf(0)))
        val sink = ByteSink()
        assertTrue(TileCodec.encodeDelta(sink, edited, base, Ref(0, 100)))
        val bytes = sink.toByteArray()
        assertTrue(bytes.size <= 20, "one-pixel delta: ${bytes.size}")
        val decoded = TileCodec.decode(ByteSource(bytes))
        assertFalse(decoded.isFull)
        assertNull(decoded.record)
        assertEquals(Ref(0, 100), decoded.base)
        assertEquals(edited, decoded.apply(base))
    }

    @Test
    fun deltaWithManyPixelsUsesMaskAndRoundTrips() {
        val random = Random(3)
        val epoch = 1_700_000_000_000L
        val base = TileRecord.build(epoch, { random.nextInt(4) }, { 60 + random.nextInt(4) })
        val positions = (0 until 256).shuffled(random).take(90).sorted().toIntArray()
        val edited =
            base.with(
                epoch + 1,
                positions,
                arrayOf(
                    IntArray(90) { 10 + random.nextInt(3) },
                    IntArray(90) { 70 },
                    IntArray(90) { random.nextInt(2) },
                    IntArray(90) { 5 },
                ),
            )
        val sink = ByteSink()
        assertTrue(TileCodec.encodeDelta(sink, edited, base, Ref(2, 7)))
        val decoded = TileCodec.decode(ByteSource(sink.toByteArray()))
        assertEquals(edited, decoded.apply(base))
        assertTrue(sink.size < full(edited).size)
    }

    @Test
    fun identicalFactsProduceNoDelta() {
        val base = TileRecord.solid(5, block = 1)
        val same = TileRecord.solid(6, block = 1)
        assertFalse(TileCodec.encodeDelta(ByteSink(), same, base, Ref(0, 0)))
    }

    @Test
    fun previousLinkReadsWithoutDecodingPixels() {
        val record = TileRecord.solid(9, block = 1)
        assertEquals(Ref(4, 12), TileCodec.previousOf(ByteSource(full(record, Ref(4, 12)))))
    }
}
