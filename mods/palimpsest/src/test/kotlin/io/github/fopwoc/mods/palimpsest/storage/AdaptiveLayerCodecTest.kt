package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdaptiveLayerCodecTest {
    @Test
    fun choosesSparseMaskedAndFullEncodings() {
        val key = TileKey(2, 3)
        val old = ByteArray(TileLayer.PIXELS)
        val one = old.copyOf().apply { this[207] = 9 }
        val sparse = checkNotNull(TileLayer.changed(key, 12, old, one))
        val sparseBody = AdaptiveLayerCodec.encode(sparse, 12)
        assertEquals(0, sparseBody[0].toInt())
        assertEquals(5, sparseBody.size)
        assertEquals(sparseBody.size, AdaptiveLayerCodec.recordLength(sparseBody, sparseBody.size))
        assertContentEquals(one, apply(old, AdaptiveLayerCodec.decode(sparseBody, key, 12)))
        assertEquals(12, AdaptiveLayerCodec.indexMetadata(sparseBody, 0).epoch)

        val many = one.copyOf()
        repeat(40) { many[it * 6] = (it + 1).toByte() }
        val masked = checkNotNull(TileLayer.changed(key, 16_400, one, many))
        val maskedBody = AdaptiveLayerCodec.encode(masked, 16_388)
        assertEquals(1, maskedBody[0].toInt())
        assertEquals(76, maskedBody.size)
        assertEquals(maskedBody.size, AdaptiveLayerCodec.recordLength(maskedBody, 43))
        assertContentEquals(many, apply(one, AdaptiveLayerCodec.decode(maskedBody, key, 16_400)))
        assertEquals(16_400, AdaptiveLayerCodec.indexMetadata(maskedBody, 12).epoch)

        val full = TileLayer.snapshot(key, 16_401, many)
        val fullBody = AdaptiveLayerCodec.encode(full, 1)
        assertEquals(2, fullBody[0].toInt())
        assertEquals(258, fullBody.size)
        assertEquals(fullBody.size, AdaptiveLayerCodec.recordLength(fullBody, 43))
        assertEquals(
            256,
            AdaptiveLayerCodec.indexMetadata(fullBody, 16_400)
                .coverage
                .sumOf(java.lang.Long::bitCount),
        )
        assertTrue(AdaptiveLayerCodec.decode(fullBody, key, 16_401).colors.contentEquals(many))
    }

    @Test
    fun rejectsTruncatedOrUnknownRecordHeaders() {
        assertFailsWith<IOException> { AdaptiveLayerCodec.recordLength(byteArrayOf(), 0) }
        assertFailsWith<IOException> { AdaptiveLayerCodec.recordLength(byteArrayOf(0, 0), 2) }
        assertFailsWith<IOException> { AdaptiveLayerCodec.recordLength(byteArrayOf(1, 0), 2) }
        assertFailsWith<IOException> { AdaptiveLayerCodec.recordLength(byteArrayOf(5, 0), 2) }
    }

    @Test
    fun solidCoverageCompressesUniformColorsWithoutChangingPixels() {
        val key = TileKey(0, 0)
        val solid = TileLayer.full(key, 0, ByteArray(TileLayer.PIXELS) { 7 })
        val fullBody = AdaptiveLayerCodec.encode(solid, 0)
        assertEquals(3, fullBody[0].toInt())
        assertEquals(3, fullBody.size)
        assertEquals(fullBody.size, AdaptiveLayerCodec.recordLength(fullBody, fullBody.size))
        assertContentEquals(solid.colors, AdaptiveLayerCodec.decode(fullBody, key, 0).colors)

        val next =
            solid.colors.copyOf().apply {
                for (z in 4 until 12) for (x in 4 until 12) this[z * 16 + x] = 9
            }
        val patch = checkNotNull(TileLayer.changed(key, 1, solid.colors, next))
        val patchBody = AdaptiveLayerCodec.encode(patch, 1)
        assertEquals(4, patchBody[0].toInt())
        assertEquals(35, patchBody.size)
        assertEquals(patchBody.size, AdaptiveLayerCodec.recordLength(patchBody, patchBody.size))
        assertContentEquals(next, apply(solid.colors, AdaptiveLayerCodec.decode(patchBody, key, 1)))

        val varied = TileLayer.full(key, 2, ByteArray(TileLayer.PIXELS) { it.toByte() })
        assertEquals(2, AdaptiveLayerCodec.encode(varied, 1)[0].toInt())
    }

    @Test
    fun fullTileExceptionsCoverAnOutlierAtTheFirstPixel() {
        val key = TileKey(0, 0)
        val colors = ByteArray(TileLayer.PIXELS) { 7 }.apply { this[0] = 9 }
        val body = AdaptiveLayerCodec.encode(TileLayer.full(key, 0, colors), 0)
        assertEquals(5, body[0].toInt())
        assertEquals(6, body.size)
        assertEquals(body.size, AdaptiveLayerCodec.recordLength(body, body.size))
        assertContentEquals(colors, AdaptiveLayerCodec.decode(body, key, 0).colors)
        val tooMany = colors.copyOf().apply { for (position in 0..16) this[position] = 9 }
        assertEquals(2, AdaptiveLayerCodec.encode(TileLayer.full(key, 0, tooMany), 0)[0].toInt())
    }

    private fun apply(base: ByteArray, layer: TileLayer): ByteArray =
        base.copyOf().also { output ->
            var color = 0
            for (position in 0 until TileLayer.PIXELS) {
                if (layer.coverage[position ushr 6] and (1L shl (position and 63)) != 0L) {
                    output[position] = layer.colors[color++]
                }
            }
        }
}
