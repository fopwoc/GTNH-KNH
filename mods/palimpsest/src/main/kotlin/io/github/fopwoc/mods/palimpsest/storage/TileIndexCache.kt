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
  private const val VERSION = 1
  const val RECORD_BYTES = 8 + 4 + 8 + 4 + TileLayer.MASK_WORDS * 8

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
        if (count < 1 || count.toLong() > fileSize / RECORD_BYTES) {
          throw IOException("Invalid record count in $file")
        }
        repeat(count) {
          val epoch = input.readLong()
          val segmentId = input.readInt()
          val offset = input.readLong()
          val length = input.readInt()
          val mask = LongArray(TileLayer.MASK_WORDS) { input.readLong() }
          if (
              epoch < 0 ||
                  segmentId !in segmentIds.indices ||
                  offset < 0 ||
                  length !in 5..AdaptiveLayerCodec.MAX_BYTES ||
                  offset > segments[segmentIds[segmentId]].size - length ||
                  mask.all { it == 0L }
          ) {
            throw IOException("Invalid record directory in $file")
          }
          record(key, epoch, segmentIds[segmentId], offset, length, mask)
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
          for (index in 0 until history.size) {
            output.writeLong(history.epochAt(index))
            output.writeInt(history.segmentAt(index))
            output.writeLong(history.offsetAt(index))
            output.writeInt(history.lengthAt(index))
            for (word in 0 until TileLayer.MASK_WORDS) output.writeLong(history.maskAt(index, word))
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
}
