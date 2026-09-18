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
    fun commonDarkGreysStayNeutralNextToRareDarkPurples() {
        val random = java.util.Random(3)
        val greys =
            List(400) {
                val v = 0x28 + random.nextInt(16)
                0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
            }
        val purples = List(3) { 0xFF3A2040.toInt() + it }
        val brights = List(300) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val palette = WorldPalette.derive(greys + purples + brights)
        val snapped = palette.argb(palette.nearest(0xFF2E2E2E.toInt()))
        val r = snapped shr 16 and 255
        val g = snapped shr 8 and 255
        val b = snapped and 255
        assertTrue(
            kotlin.math.abs(r - g) <= 6 && kotlin.math.abs(g - b) <= 6,
            "snapped=%08X".format(snapped),
        )
        assertTrue(kotlin.math.abs(g - 0x2E) <= 10, "snapped=%08X".format(snapped))
    }

    @Test
    fun twoNearbyGreysUsedAsFloorAndBorderStayDistinct() {
        val random = java.util.Random(5)
        val noise = List(1500) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val floor = 0xFF2E2E2E.toInt()
        val border = 0xFF3A3A3A.toInt()
        val palette = WorldPalette.derive(noise + floor + border)
        assertTrue(palette.nearest(floor) != palette.nearest(border))
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
