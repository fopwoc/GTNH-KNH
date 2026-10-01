package io.github.fopwoc.mods.palimpsest.render

import io.github.fopwoc.mods.palimpsest.tree.Sample
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PageShadingTest {
    private val shader =
        TerrainShader(
            { it * 7919 and 0xFFFFFF },
            { it % 3 },
            { it * 397 and 0xFFFFFF },
        )

    @Test
    fun patchesAndDenseFallbackMatchFullShadingWithoutChangingPreviousInputsOrPixels() {
        val random = Random(432)
        var grid = SampleGrid(128)
        for (z in -1 until 128) for (x in -1 until 128) grid.set(
            x,
            z,
            Sample(
                1 + random.nextInt(255),
                random.nextInt(256),
                random.nextInt(32),
                random.nextInt(256),
            ),
        )
        val shading = PageShading(shader)
        var previous = shading.build(grid, null)
        repeat(100) { step ->
            val oldFacts = grid.block.copyOf()
            val oldPixels = checkNotNull(previous.raster).copyPixels()
            grid = grid.copy()
            repeat(if (step % 10 == 0) 8000 else 2) {
                val x = random.nextInt(-1, 128)
                val z = random.nextInt(-1, 128)
                if (step % 11 == 0) grid.set(x, z, SampleGrid.NONE, 0, 0, 0)
                else
                    grid.set(
                        x,
                        z,
                        Sample(
                            random.nextInt(256),
                            random.nextInt(256),
                            random.nextInt(256),
                            random.nextInt(65536),
                        ),
                    )
            }
            val result = shading.build(grid, previous)
            val expected = ByteArray(128 * 128 * 4).also { shader.shade(grid, it) }
            assertContentEquals(expected, checkNotNull(result.raster).copyPixels())
            assertContentEquals(oldFacts, checkNotNull(previous.grid).block)
            assertContentEquals(oldPixels, checkNotNull(previous.raster).copyPixels())
            if (step % 10 == 0) assertEquals(16384, result.shadedPixels)
            else assertTrue(result.shadedPixels <= 384)
            previous = result
        }
        val unchanged = shading.build(grid.copy(), previous)
        assertSame(previous.raster, unchanged.raster)
        assertEquals(0, unchanged.shadedPixels)
        val corner = grid.copy().apply { set(-1, -1, Sample(400, 200, 0, 0)) }
        val invisible = shading.build(corner, unchanged)
        assertSame(previous.raster, invisible.raster)
        assertEquals(0, invisible.shadedPixels)
    }
}
