package io.github.fopwoc.mods.palimpsest.storage

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

/** Disposable record directory keyed by the exact set of immutable segment files. */
internal object TileIndexCache {
  data class Segment(val path: Path, val size: Long)

  private const val MAGIC = 0x50494458 // PIDX
  private const val VERSION = 2
  private const val FULL_MASK = 0
  private const val RAW_MASK = 9

  fun path(directory: Path): Path = directory.resolve(".index-cache.pidx")

  fun load(
      file: Path,
      segments: List<Segment>,
      record: (TileKey, Long, Int, Long, Int, LongArray) -> Unit,
  ): Boolean {
    if (!Files.isRegularFile(file)) return false
    val fileSize = Files.size(file)
    if (fileSize < 20) throw IOException("Truncated index cache $file")
    val checked = CheckedInputStream(BufferedInputStream(Files.newInputStream(file)), CRC32())
    DataInputStream(checked).use { input ->
      if (input.readInt() != MAGIC || input.readInt() != VERSION) {
        throw IOException("Unsupported index cache $file")
      }
      val segmentCount = input.readInt()
      if (segmentCount != segments.size) return false
      val actual = segments.withIndex().associateBy { it.value.path.fileName.toString() }
      val segmentIds = IntArray(segmentCount)
      val seen = HashSet<String>()
      for (oldId in 0 until segmentCount) {
        val name = input.readUTF()
        val size = input.readLong()
        val current = actual[name] ?: return false
        if (!seen.add(name) || current.value.size != size) return false
        segmentIds[oldId] = current.index
      }
      val tileCount = input.readInt()
      if (tileCount < 0 || tileCount.toLong() > fileSize / 12) {
        throw IOException("Invalid tile count in $file")
      }
      var entries = 0L
      repeat(tileCount) {
        val key = TileKey(input.readInt(), input.readInt())
        val count = input.readInt()
        if (count < 1 || count.toLong() > fileSize / 5) {
          throw IOException("Invalid record count in $file")
        }
        var previousEpoch = 0L
        repeat(count) { recordIndex ->
          val delta = readVarLong(input)
          if (delta > Long.MAX_VALUE - previousEpoch) throw IOException("Epoch overflow in $file")
          val epoch = previousEpoch + delta
          val segmentId = readVarLong(input).toIntExact(file)
          val offset = readVarLong(input)
          val length = readVarLong(input).toIntExact(file)
          val mask = readMask(input, file)
          if (
              (recordIndex > 0 && epoch <= previousEpoch) ||
                  segmentId !in segmentIds.indices ||
                  offset < 0 ||
                  length !in 5..AdaptiveLayerCodec.MAX_BYTES ||
                  offset > segments[segmentIds[segmentId]].size - length ||
                  mask.all { it == 0L }
          ) {
            throw IOException("Invalid record directory in $file")
          }
          record(key, epoch, segmentIds[segmentId], offset, length, mask)
          previousEpoch = epoch
          entries++
        }
      }
      if (entries == 0L && segments.isNotEmpty()) throw IOException("Empty index cache $file")
      val expected = checked.checksum.value.toInt()
      if (input.readInt() != expected || input.read() != -1) {
        throw IOException("Index cache checksum mismatch: $file")
      }
      return true
    }
  }

  fun save(file: Path, segments: List<Segment>, histories: Map<TileKey, PackedTileHistory>) {
    Files.createDirectories(file.parent)
    val temporary = Files.createTempFile(file.parent, ".index-", ".tmp")
    try {
      val checked =
          CheckedOutputStream(BufferedOutputStream(Files.newOutputStream(temporary)), CRC32())
      DataOutputStream(checked).use { output ->
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(segments.size)
        for (segment in segments) {
          output.writeUTF(segment.path.fileName.toString())
          output.writeLong(segment.size)
        }
        output.writeInt(histories.size)
        for ((key, history) in histories.toSortedMap(compareBy(TileKey::z, TileKey::x))) {
          output.writeInt(key.x)
          output.writeInt(key.z)
          output.writeInt(history.size)
          var previousEpoch = 0L
          for (index in 0 until history.size) {
            val epoch = history.epochAt(index)
            writeVarLong(output, epoch - previousEpoch)
            writeVarLong(output, history.segmentAt(index).toLong())
            writeVarLong(output, history.offsetAt(index))
            writeVarLong(output, history.lengthAt(index).toLong())
            writeMask(output, history, index)
            previousEpoch = epoch
          }
        }
        output.writeInt(checked.checksum.value.toInt())
      }
      FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
      Files.move(
          temporary,
          file,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
      )
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private fun writeMask(output: DataOutputStream, history: PackedTileHistory, index: Int) {
    val count = (0 until TileLayer.MASK_WORDS).sumOf { java.lang.Long.bitCount(history.maskAt(index, it)) }
    when {
      count == TileLayer.PIXELS -> output.writeByte(FULL_MASK)
      count <= 8 -> {
        output.writeByte(count)
        for (position in 0 until TileLayer.PIXELS) {
          if (history.maskAt(index, position ushr 6) and (1L shl (position and 63)) != 0L) {
            output.writeByte(position)
          }
        }
      }
      else -> {
        output.writeByte(RAW_MASK)
        for (word in 0 until TileLayer.MASK_WORDS) output.writeLong(history.maskAt(index, word))
      }
    }
  }

  private fun readMask(input: DataInputStream, file: Path): LongArray {
    val mask = LongArray(TileLayer.MASK_WORDS)
    when (val kind = input.readUnsignedByte()) {
      FULL_MASK -> mask.fill(-1L)
      in 1..8 -> {
        var previous = -1
        repeat(kind) {
          val position = input.readUnsignedByte()
          if (position <= previous) throw IOException("Unsorted index mask in $file")
          mask[position ushr 6] = mask[position ushr 6] or (1L shl (position and 63))
          previous = position
        }
      }
      RAW_MASK -> for (word in mask.indices) mask[word] = input.readLong()
      else -> throw IOException("Invalid index mask kind $kind in $file")
    }
    return mask
  }

  private fun writeVarLong(output: DataOutputStream, value: Long) {
    require(value >= 0)
    var remaining = value
    while (remaining >= 128) {
      output.writeByte(((remaining and 127) or 128).toInt())
      remaining = remaining ushr 7
    }
    output.writeByte(remaining.toInt())
  }

  private fun readVarLong(input: DataInputStream): Long {
    var value = 0L
    for (shift in 0..63 step 7) {
      val byte = input.readUnsignedByte()
      if (shift == 63 && byte > 0) throw IOException("Index varint overflow")
      value = value or ((byte and 127).toLong() shl shift)
      if (byte and 128 == 0) return value
    }
    throw IOException("Index varint too long")
  }

  private fun Long.toIntExact(file: Path): Int {
    if (this > Int.MAX_VALUE) throw IOException("Index integer overflow in $file")
    return toInt()
  }
}
