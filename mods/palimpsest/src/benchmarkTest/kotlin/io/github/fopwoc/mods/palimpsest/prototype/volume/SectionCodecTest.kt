package io.github.fopwoc.mods.palimpsest.prototype.volume

import io.github.fopwoc.mods.palimpsest.prototype.volume.model.ChunkVolume
import io.github.fopwoc.mods.palimpsest.prototype.volume.storage.SectionCodec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SectionCodecTest {
    private fun roundTrip(blocks: IntArray): ByteArray {
        val encoded = SectionCodec.encode(blocks)
        assertContentEquals(blocks, SectionCodec.decode(encoded))
        assertContentEquals(encoded, SectionCodec.encode(blocks.copyOf()))
        return encoded
    }

    @Test
    fun uniformSectionIsAPaletteAlone() {
        assertTrue(roundTrip(IntArray(ChunkVolume.SECTION_BLOCKS) { 7 }).size <= 2)
    }

    @Test
    fun layeredTerrainWithOresAndCavesRoundTrips() {
        val random = Random(3)
        val blocks = IntArray(ChunkVolume.SECTION_BLOCKS) { at ->
            val y = at shr 8
            when {
                random.nextInt(40) == 0 -> 1000 + random.nextInt(20)
                y > 12 -> 3
                random.nextInt(10) == 0 -> 0
                else -> 1
            }
        }
        val encoded = roundTrip(blocks)
        assertTrue(encoded.size < SectionCodec.deflatedSize(blocks), "range coding beats deflate here")
    }

    @Test
    fun everyPaletteShapeRoundTrips() {
        val random = Random(9)
        for (palette in listOf(2, 3, 24, 25, 200, 256, 257, 4096)) {
            roundTrip(IntArray(ChunkVolume.SECTION_BLOCKS) { random.nextInt(palette) * 13 + 1 })
            roundTrip(IntArray(ChunkVolume.SECTION_BLOCKS) { (it % palette) * 7 })
        }
    }
}
