package io.github.fopwoc.palimpsest.db.surface

import io.github.fopwoc.palimpsest.db.BlockKind
import io.github.fopwoc.palimpsest.db.SectionBlocks
import kotlin.test.Test
import kotlin.test.assertEquals

class SurfaceScanTest {
    private val stone = 1
    private val water = 2
    private val flower = 3
    private val lava = 4
    private val glass = 5
    private val kinds =
        mapOf(
            0 to BlockKind.AIR,
            stone to BlockKind.SOLID,
            water to BlockKind.WATER,
            flower to BlockKind.DECORATION,
            lava to BlockKind.LIQUID,
            glass to BlockKind.TRANSPARENT,
        )

    /** One column at x = z = 0 built from [blocks] by Y, everything else air. */
    private fun scan(blocks: Map<Int, Int>, minSection: Int = 0): Triple<Int, Int, Int> {
        val sections = arrayOfNulls<IntArray>(16)
        for ((y, block) in blocks) {
            val section =
                sections[(y - minSection * 16) shr 4]
                    ?: IntArray(SectionBlocks.VOLUME).also {
                        sections[(y - minSection * 16) shr 4] = it
                    }
            section[SectionBlocks.index(0, (y - minSection * 16) and 15, 0)] = block
        }
        val surface = SurfaceScan.scan(sections, minSection, IntArray(256), { kinds.getValue(it) })
        return Triple(surface.block[0], surface.height[0], surface.depth[0])
    }

    @Test
    fun `land shows its top block`() =
        assertEquals(Triple(stone, 64, 0), scan((0..64).associateWith { stone }))

    @Test
    fun `water shows the floor with the depth on top`() =
        assertEquals(
            Triple(stone, 59, 4),
            scan((0..59).associateWith { stone } + (60..63).associateWith { water }),
        )

    @Test
    fun `a decoration keeps the height of its ground`() =
        assertEquals(
            Triple(flower, 64, 0),
            scan((0..64).associateWith { stone } + mapOf(65 to flower)),
        )

    @Test
    fun `a plant under water is not the floor`() =
        assertEquals(
            Triple(stone, 59, 3),
            scan(
                (0..59).associateWith { stone } +
                    mapOf(60 to flower) +
                    (61..63).associateWith { water }
            ),
        )

    @Test
    fun `other liquids and see-through blocks`() {
        assertEquals(Triple(lava, 20, 0), scan((0..19).associateWith { stone } + mapOf(20 to lava)))
        assertEquals(
            Triple(stone, 64, 0),
            scan((0..64).associateWith { stone } + mapOf(70 to glass)),
        )
    }

    @Test
    fun `nothing at all is air at the bottom`() =
        assertEquals(Triple(0, -64, 0), scan(emptyMap(), minSection = -4))

    @Test
    fun `surfaces round-trip through their codec`() {
        val surface =
            SurfaceScan.scan(
                arrayOf(IntArray(SectionBlocks.VOLUME) { if (it shr 8 < 5) stone else 0 }),
                -4,
                IntArray(256) { it % 3 },
                { kinds.getValue(it) },
            )
        val decoded = Surface.decode(surface.encode(-64))
        assertEquals(surface.block.toList(), decoded.block.toList())
        assertEquals(surface.height.toList(), decoded.height.toList())
        assertEquals(surface.biome.toList(), decoded.biome.toList())
    }
}
