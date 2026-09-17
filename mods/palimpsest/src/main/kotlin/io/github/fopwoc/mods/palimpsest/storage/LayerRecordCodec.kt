package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stable, endian-explicit record body; the segment supplies length and CRC32C. */
internal object LayerRecordCodec {
  const val FIXED_BYTES =
      Long.SIZE_BYTES + Int.SIZE_BYTES * 2 + Long.SIZE_BYTES * TileLayer.MASK_WORDS
  const val MAX_BYTES = FIXED_BYTES + TileLayer.PIXELS

  fun encode(layer: TileLayer): ByteArray =
      ByteBuffer.allocate(FIXED_BYTES + layer.colors.size)
          .order(ByteOrder.LITTLE_ENDIAN)
          .putLong(layer.epoch)
          .putInt(layer.key.x)
          .putInt(layer.key.z)
          .apply { layer.coverage.forEach(::putLong) }
          .put(layer.colors)
          .array()

  fun decode(bytes: ByteArray): TileLayer {
    if (bytes.size !in (FIXED_BYTES + 1)..MAX_BYTES) throw IOException("Invalid layer length")
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val epoch = buffer.long
    val key = TileKey(buffer.int, buffer.int)
    val coverage = LongArray(TileLayer.MASK_WORDS) { buffer.long }
    val colors = ByteArray(buffer.remaining()).also(buffer::get)
    return try {
      TileLayer(key, epoch, coverage, colors)
    } catch (failure: IllegalArgumentException) {
      throw IOException("Invalid layer coverage", failure)
    }
  }
}
