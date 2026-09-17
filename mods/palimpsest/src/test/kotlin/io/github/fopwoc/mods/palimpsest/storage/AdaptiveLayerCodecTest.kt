package io.github.fopwoc.mods.palimpsest.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
    assertContentEquals(one, apply(old, AdaptiveLayerCodec.decode(sparseBody, key, 12)))
    assertEquals(12, AdaptiveLayerCodec.indexMetadata(sparseBody, 0).epoch)

    val many = one.copyOf()
    repeat(40) { many[it * 6] = (it + 1).toByte() }
    val masked = checkNotNull(TileLayer.changed(key, 16_400, one, many))
    val maskedBody = AdaptiveLayerCodec.encode(masked, 16_388)
    assertEquals(1, maskedBody[0].toInt())
    assertEquals(76, maskedBody.size)
    assertContentEquals(many, apply(one, AdaptiveLayerCodec.decode(maskedBody, key, 16_400)))
    assertEquals(16_400, AdaptiveLayerCodec.indexMetadata(maskedBody, 12).epoch)

    val full = TileLayer.snapshot(key, 16_401, many)
    val fullBody = AdaptiveLayerCodec.encode(full, 1)
    assertEquals(2, fullBody[0].toInt())
    assertEquals(258, fullBody.size)
    assertEquals(
        256,
        AdaptiveLayerCodec.indexMetadata(fullBody, 16_400).coverage.sumOf(java.lang.Long::bitCount),
    )
    assertTrue(AdaptiveLayerCodec.decode(fullBody, key, 16_401).colors.contentEquals(many))
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
