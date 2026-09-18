package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Record body: encoding kind, per-tile epoch delta, and adaptive pixel coverage. */
internal object AdaptiveLayerCodec {
  private const val SPARSE = 0
  private const val MASKED = 1
  private const val FULL = 2
  private const val FULL_SOLID = 3
  private const val MASKED_SOLID = 4
  const val MAX_BYTES = 1 + 10 + TileLayer.MASK_WORDS * Long.SIZE_BYTES + TileLayer.PIXELS

  data class IndexMetadata(val epoch: Long, val coverage: LongArray)

  /** The encoding header and coverage determine the complete body length without framing bytes. */
  fun recordLength(prefix: ByteArray, available: Int): Int {
    val buffer = ByteBuffer.wrap(prefix, 0, available).order(ByteOrder.LITTLE_ENDIAN)
    if (!buffer.hasRemaining()) throw IOException("Missing layer encoding")
    val kind = buffer.get().toInt() and 255
    getVarLong(buffer)
    val headerLength = buffer.position()
    val payloadLength =
        when (kind) {
          SPARSE -> {
            if (!buffer.hasRemaining()) throw IOException("Missing sparse pixel count")
            val count = buffer.get().toInt() and 255
            if (count !in 1..31) throw IOException("Invalid sparse pixel count")
            1 + count * 2
          }
          MASKED -> {
            if (buffer.remaining() < TileLayer.MASK_WORDS * Long.SIZE_BYTES) {
              throw IOException("Truncated coverage mask")
            }
            val count =
                (0 until TileLayer.MASK_WORDS).sumOf { java.lang.Long.bitCount(buffer.long) }
            if (count !in 1 until TileLayer.PIXELS) throw IOException("Invalid masked coverage")
            TileLayer.MASK_WORDS * Long.SIZE_BYTES + count
          }
          FULL -> TileLayer.PIXELS
          FULL_SOLID -> 1
          MASKED_SOLID -> {
            if (buffer.remaining() < TileLayer.MASK_WORDS * Long.SIZE_BYTES) {
              throw IOException("Truncated coverage mask")
            }
            val count =
                (0 until TileLayer.MASK_WORDS).sumOf { java.lang.Long.bitCount(buffer.long) }
            if (count !in 32 until TileLayer.PIXELS) {
              throw IOException("Invalid solid masked coverage")
            }
            TileLayer.MASK_WORDS * Long.SIZE_BYTES + 1
          }
          else -> throw IOException("Unknown layer encoding $kind")
        }
    return headerLength + payloadLength
  }

  fun encode(layer: TileLayer, epochDelta: Long): ByteArray {
    require(epochDelta >= 0)
    val kind =
        when {
          layer.colors.size == TileLayer.PIXELS && layer.colors.all { it == layer.colors[0] } ->
              FULL_SOLID
          layer.colors.size == TileLayer.PIXELS -> FULL
          layer.colors.size <= 31 -> SPARSE
          layer.colors.all { it == layer.colors[0] } -> MASKED_SOLID
          else -> MASKED
        }
    val payload =
        when (kind) {
          SPARSE -> 1 + layer.colors.size * 2
          MASKED -> TileLayer.MASK_WORDS * Long.SIZE_BYTES + layer.colors.size
          MASKED_SOLID -> TileLayer.MASK_WORDS * Long.SIZE_BYTES + 1
          FULL_SOLID -> 1
          else -> TileLayer.PIXELS
        }
    val buffer =
        ByteBuffer.allocate(1 + varLongSize(epochDelta) + payload).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put(kind.toByte())
    putVarLong(buffer, epochDelta)
    when (kind) {
      SPARSE -> {
        buffer.put(layer.colors.size.toByte())
        var color = 0
        for (position in 0 until TileLayer.PIXELS) {
          if (layer.coverage[position ushr 6] and (1L shl (position and 63)) == 0L) continue
          buffer.put(position.toByte())
          buffer.put(layer.colors[color++])
        }
      }
      MASKED -> {
        layer.coverage.forEach(buffer::putLong)
        buffer.put(layer.colors)
      }
      MASKED_SOLID -> {
        layer.coverage.forEach(buffer::putLong)
        buffer.put(layer.colors[0])
      }
      FULL_SOLID -> buffer.put(layer.colors[0])
      else -> buffer.put(layer.colors)
    }
    return buffer.array()
  }

  fun indexMetadata(body: ByteArray, previousEpoch: Long): IndexMetadata {
    val scanned = scan(body, collectColors = false)
    if (scanned.delta > Long.MAX_VALUE - previousEpoch) throw IOException("Epoch overflow")
    return IndexMetadata(previousEpoch + scanned.delta, scanned.coverage)
  }

  fun decode(body: ByteArray, key: TileKey, epoch: Long): TileLayer {
    val scanned = scan(body, collectColors = true)
    return TileLayer(key, epoch, scanned.coverage, checkNotNull(scanned.colors))
  }

  private data class Scanned(val delta: Long, val coverage: LongArray, val colors: ByteArray?)

  private fun scan(body: ByteArray, collectColors: Boolean): Scanned {
    if (body.size !in 3..MAX_BYTES) throw IOException("Invalid adaptive layer length")
    val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
    val kind = buffer.get().toInt() and 255
    val delta = getVarLong(buffer)
    val coverage = LongArray(TileLayer.MASK_WORDS)
    val colors: ByteArray?
    when (kind) {
      SPARSE -> {
        if (!buffer.hasRemaining()) throw IOException("Missing sparse pixel count")
        val count = buffer.get().toInt() and 255
        if (count !in 1..31 || buffer.remaining() != count * 2) {
          throw IOException("Invalid sparse pixel count")
        }
        colors = if (collectColors) ByteArray(count) else null
        var previousPosition = -1
        repeat(count) { index ->
          val position = buffer.get().toInt() and 255
          if (position <= previousPosition) throw IOException("Unsorted sparse pixels")
          coverage[position ushr 6] = coverage[position ushr 6] or (1L shl (position and 63))
          val color = buffer.get()
          if (colors != null) colors[index] = color
          previousPosition = position
        }
      }
      MASKED -> {
        if (buffer.remaining() !in 33..(32 + TileLayer.PIXELS)) {
          throw IOException("Invalid masked layer length")
        }
        for (word in coverage.indices) coverage[word] = buffer.long
        val count = coverage.sumOf(java.lang.Long::bitCount)
        if (count != buffer.remaining()) throw IOException("Invalid masked coverage")
        colors = if (collectColors) ByteArray(count).also(buffer::get) else null
      }
      FULL -> {
        if (buffer.remaining() != TileLayer.PIXELS) throw IOException("Invalid full layer length")
        coverage.fill(-1L)
        colors = if (collectColors) ByteArray(TileLayer.PIXELS).also(buffer::get) else null
      }
      FULL_SOLID -> {
        if (buffer.remaining() != 1) throw IOException("Invalid solid full layer length")
        coverage.fill(-1L)
        val color = buffer.get()
        colors = if (collectColors) ByteArray(TileLayer.PIXELS) { color } else null
      }
      MASKED_SOLID -> {
        if (buffer.remaining() != TileLayer.MASK_WORDS * Long.SIZE_BYTES + 1) {
          throw IOException("Invalid solid masked layer length")
        }
        for (word in coverage.indices) coverage[word] = buffer.long
        val count = coverage.sumOf(java.lang.Long::bitCount)
        if (count !in 32 until TileLayer.PIXELS) throw IOException("Invalid solid masked coverage")
        val color = buffer.get()
        colors = if (collectColors) ByteArray(count) { color } else null
      }
      else -> throw IOException("Unknown layer encoding $kind")
    }
    return Scanned(delta, coverage, colors)
  }

  private fun varLongSize(value: Long): Int {
    var left = value
    var size = 1
    while (left >= 128) {
      left = left ushr 7
      size++
    }
    return size
  }

  private fun putVarLong(buffer: ByteBuffer, value: Long) {
    var left = value
    while (left >= 128) {
      buffer.put(((left and 127) or 128).toByte())
      left = left ushr 7
    }
    buffer.put(left.toByte())
  }

  private fun getVarLong(buffer: ByteBuffer): Long {
    var value = 0L
    for (shift in 0..63 step 7) {
      if (!buffer.hasRemaining()) throw IOException("Truncated epoch delta")
      val byte = buffer.get().toInt() and 255
      if (shift == 63 && byte > 0) throw IOException("Epoch delta overflow")
      value = value or ((byte and 127).toLong() shl shift)
      if (byte and 128 == 0) return value
    }
    throw IOException("Epoch delta too long")
  }
}
