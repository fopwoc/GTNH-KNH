package io.github.fopwoc.mods.framework.world

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldPaletteTest {
    @Test
    fun derivedPaletteKeepsEveryShadeOfFewColorsWithinABucketAndSnapsOthersNearby() {
        val colors = listOf(0xFF808080.toInt(), 0xFF5FA83A.toInt(), 0xFF3F5FDF.toInt())
        val palette = WorldPalette.derive(colors)
        assertEquals(0, palette.argb(0))
        for (color in colors) for (shade in WorldPalette.SHADES) {
            val exact = WorldPalette.shade(color, shade)
            val snapped = palette.argb(palette.nearest(exact))
            for (shift in listOf(16, 8, 0)) {
                assertTrue(
                    kotlin.math.abs((exact shr shift and 255) - (snapped shr shift and 255)) <= 8
                )
            }
        }
        assertEquals(0, palette.nearest(0))
        val grey = palette.argb(palette.nearest(0xFF808080.toInt()))
        assertEquals(grey, palette.argb(palette.nearest(0xFF838283.toInt())))
    }

    @Test
    fun thousandsOfColorsReduceTo255EntriesCoveringTheirRange() {
        val random = java.util.Random(7)
        val colors = List(6_000) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val palette = WorldPalette.derive(colors)
        val distinct = palette.argb.toSet()
        assertTrue(distinct.size in 200..256, "distinct=${distinct.size}")
        for (color in colors.take(200)) {
            val snapped = palette.argb(palette.nearest(color))
            val error =
                listOf(16, 8, 0).maxOf { shift ->
                    kotlin.math.abs((color shr shift and 255) - (snapped shr shift and 255))
                }
            assertTrue(error < 64, "error=$error")
        }
    }

    @Test
    fun paletteFileRoundTrips() {
        val directory = Files.createTempDirectory("palette-")
        try {
            val file = directory.resolve("palette.bin")
            val created =
                WorldPalette.loadOrCreate(file) { WorldPalette.derive(listOf(0xFF102030.toInt())) }
            assertTrue(Files.isRegularFile(file))
            val loaded = WorldPalette.loadOrCreate(file) { error("must not derive again") }
            assertContentEquals(created.argb, loaded.argb)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
        }
    }
}
