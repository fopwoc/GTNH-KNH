package io.github.fopwoc.mods.palimpsest.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.LinkedHashMap

/** Immutable, content-addressed segments with a rebuildable in-memory tile/time index. */
class TileHistoryStore(private val directory: Path) : AutoCloseable {
  private data class WrittenRecord(
      val key: TileKey,
      val offset: Long,
      val length: Int,
      val epoch: Long,
      val coverage: LongArray,
  )

  data class TileRead(
      val colors: ByteArray,
      val layersVisited: Int,
      val layersDecoded: Int,
      val layersSkipped: Int,
  )

  data class SampleRead(
      val colors: ByteArray,
      val bytesRead: Int,
      val layersVisited: Int,
      val layersDecoded: Int,
  )

  data class AppendResult(
      val layersWritten: Int,
      val layersDiscarded: Int,
      val coveredCells: Int,
      val bytesAdded: Long,
  )

  private val index = HashMap<TileKey, PackedTileHistory>()
  private val channels = ArrayList<FileChannel>()
  private val latestTiles =
      object : LinkedHashMap<TileKey, ByteArray>(256, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<TileKey, ByteArray>
        ): Boolean = size > 4096
      }
  private var bytes = 0L
  private var records = 0
  private var latest = 0L

  init {
    reload()
  }

  val tileCount: Int
    @Synchronized get() = index.size

  val layerCount: Int
    @Synchronized get() = records

  val byteCount: Long
    @Synchronized get() = bytes

  val indexArrayBytes: Long
    @Synchronized get() = index.values.sumOf(PackedTileHistory::arrayBytes)

  val latestEpoch: Long
    @Synchronized get() = latest

  @Synchronized
  fun append(layers: List<TileLayer>): AppendResult {
    if (layers.isEmpty()) return AppendResult(0, 0, 0, 0)
    val lastInBatch = HashMap<TileKey, Long>()
    for (layer in layers) {
      val previous = lastInBatch[layer.key] ?: index[layer.key]?.lastEpoch ?: -1L
      require(layer.epoch > previous) { "Tile epochs must increase for ${layer.key}" }
      lastInBatch[layer.key] = layer.epoch
    }
    val normalized =
        LayerNormalizer.normalize(layers) { key ->
          latestTiles[key] ?: index[key]?.let { read(key, it.lastEpoch)?.colors }
        }
    val writing = normalized.layers
    if (writing.isEmpty()) return AppendResult(0, layers.size, 0, 0)
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".palimpsest-", ".tmp")
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      val written = ArrayList<WrittenRecord>(writing.size)
      val groups = writing.groupBy(TileLayer::key).toSortedMap(compareBy(TileKey::z, TileKey::x))
      var offset = MAGIC.size.toLong() + Int.SIZE_BYTES
      FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
        write(output, MAGIC, digest)
        write(output, leInt(groups.size), digest)
        for ((key, history) in groups) {
          val groupHeader =
              ByteBuffer.allocate(12)
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .putInt(key.x)
                  .putInt(key.z)
                  .putInt(history.size)
                  .array()
          write(output, groupHeader, digest)
          offset += groupHeader.size
          var previousEpoch = 0L
          for (layer in history) {
            val body = AdaptiveLayerCodec.encode(layer, layer.epoch - previousEpoch)
            write(output, body, digest)
            written += WrittenRecord(key, offset, body.size, layer.epoch, layer.coverage.copyOf())
            offset += body.size
            previousEpoch = layer.epoch
          }
        }
        output.force(true)
      }
      val name = digest.digest().joinToString("") { "%02x".format(it) } + EXTENSION
      val sealed = directory.resolve(name)
      if (Files.exists(sealed)) throw IOException("Segment already exists: $sealed")
      Files.move(temporary, sealed, StandardCopyOption.ATOMIC_MOVE)
      val channel = FileChannel.open(sealed, StandardOpenOption.READ)
      val segmentId = channels.size
      channels += channel
      for (entry in written) {
        index
            .getOrPut(entry.key, PackedTileHistory::forAppend)
            .add(entry.epoch, segmentId, entry.offset, entry.length, entry.coverage)
      }
      records += writing.size
      latest = maxOf(latest, writing.maxOf(TileLayer::epoch))
      bytes += offset
      latestTiles.putAll(normalized.latest)
      return AppendResult(
          writing.size,
          layers.size - writing.size,
          writing.sumOf { it.colors.size },
          offset,
      )
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  @Synchronized
  fun read(key: TileKey, epoch: Long): TileRead? {
    val history = index[key] ?: return null
    val firstAfter = history.firstAfter(epoch)
    if (firstAfter == 0) return null
    val result = ByteArray(TileLayer.PIXELS)
    val missing = LongArray(TileLayer.MASK_WORDS) { -1L }
    var visited = 0
    var decoded = 0
    var skipped = 0
    var entryIndex = firstAfter - 1
    while (entryIndex >= 0) {
      val group = entryIndex / PackedTileHistory.GROUP_SIZE
      if (!history.groupCanFill(group, missing)) {
        skipped += entryIndex - group * PackedTileHistory.GROUP_SIZE + 1
        entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
        continue
      }
      val groupStart = group * PackedTileHistory.GROUP_SIZE
      while (entryIndex >= groupStart) {
        visited++
        if (history.layerCanFill(entryIndex, missing)) {
          val layer = readLayer(key, history, entryIndex)
          decoded++
          var colorIndex = 0
          for (position in 0 until TileLayer.PIXELS) {
            val bit = 1L shl (position and 63)
            val word = position ushr 6
            if (layer.coverage[word] and bit == 0L) continue
            if (missing[word] and bit != 0L) result[position] = layer.colors[colorIndex]
            colorIndex++
          }
          for (word in missing.indices) missing[word] = missing[word] and layer.coverage[word].inv()
          if (missing.all { it == 0L }) return TileRead(result, visited, decoded, skipped)
        }
        entryIndex--
      }
    }
    throw IOException("Tile $key has no complete initial layer")
  }

  /** Resolves selected indexed colors using coverage masks and positional record reads. */
  @Synchronized
  fun readSamples(key: TileKey, epoch: Long, positions: IntArray): SampleRead? {
    require(positions.isNotEmpty() && positions.size <= TileLayer.PIXELS)
    require(positions.all { it in 0 until TileLayer.PIXELS })
    require((1 until positions.size).all { positions[it - 1] < positions[it] })
    val history = index[key] ?: return null
    var entryIndex = history.firstAfter(epoch) - 1
    if (entryIndex < 0) return null
    val missing = LongArray(TileLayer.MASK_WORDS)
    for (position in positions) {
      missing[position ushr 6] = missing[position ushr 6] or (1L shl (position and 63))
    }
    val colors = ByteArray(positions.size)
    var bytesRead = 0
    var visited = 0
    var decoded = 0
    while (entryIndex >= 0) {
      val group = entryIndex / PackedTileHistory.GROUP_SIZE
      if (!history.groupCanFill(group, missing)) {
        entryIndex = group * PackedTileHistory.GROUP_SIZE - 1
        continue
      }
      val groupStart = group * PackedTileHistory.GROUP_SIZE
      while (entryIndex >= groupStart) {
        visited++
        if (history.layerCanFill(entryIndex, missing)) {
          bytesRead += readLayerSamples(history, entryIndex, positions, missing, colors)
          decoded++
          if (missing.all { it == 0L }) return SampleRead(colors, bytesRead, visited, decoded)
        }
        entryIndex--
      }
    }
    throw IOException("Tile $key has no complete initial layer")
  }

  @Synchronized
  fun readPixel(key: TileKey, epoch: Long, position: Int): Int? =
      readSamples(key, epoch, intArrayOf(position))?.colors?.get(0)?.toInt()?.and(255)

  private fun readLayerSamples(
      history: PackedTileHistory,
      index: Int,
      positions: IntArray,
      missing: LongArray,
      output: ByteArray,
  ): Int {
    val length = history.lengthAt(index)
    val channel = channels[history.segmentAt(index)]
    val headerSize = minOf(length, 12)
    val header = ByteBuffer.allocate(headerSize)
    readFully(channel, history.offsetAt(index), header)
    val prefix = header.array()
    // The first byte is the kind; epoch varint begins at byte one.
    var payload = 1
    while (payload < headerSize) {
      val value = prefix[payload++].toInt() and 255
      if (value and 128 == 0) break
    }
    if (payload >= headerSize && prefix[payload - 1].toInt() and 128 != 0) {
      throw IOException("Invalid sample record header")
    }
    val kind = prefix[0].toInt() and 255
    val selected = ArrayList<Pair<Int, Int>>()
    var firstOffset = length
    var lastOffset = -1
    for ((resultIndex, position) in positions.withIndex()) {
      val word = position ushr 6
      val bit = 1L shl (position and 63)
      if (missing[word] and bit == 0L || history.maskAt(index, word) and bit == 0L) continue
      var rank = 0
      for (earlier in 0 until word) rank += java.lang.Long.bitCount(history.maskAt(index, earlier))
      rank += java.lang.Long.bitCount(history.maskAt(index, word) and (bit - 1))
      val offset =
          when (kind) {
            0 -> payload + 1 + rank * 2 + 1
            1 -> payload + TileLayer.MASK_WORDS * Long.SIZE_BYTES + rank
            2 -> payload + position
            else -> throw IOException("Unknown sample record kind $kind")
          }
      if (offset !in 0 until length) throw IOException("Invalid sample color offset")
      selected += resultIndex to offset
      firstOffset = minOf(firstOffset, offset)
      lastOffset = maxOf(lastOffset, offset)
    }
    if (selected.isEmpty()) return headerSize
    val payloadBytes =
        if (lastOffset < headerSize) {
          ByteBuffer.wrap(prefix, firstOffset, lastOffset - firstOffset + 1).slice()
        } else {
          ByteBuffer.allocate(lastOffset - firstOffset + 1).also {
            readFully(channel, history.offsetAt(index) + firstOffset, it)
          }
        }
    for ((resultIndex, offset) in selected) {
      val position = positions[resultIndex]
      output[resultIndex] = payloadBytes.get(offset - firstOffset)
      missing[position ushr 6] = missing[position ushr 6] and (1L shl (position and 63)).inv()
    }
    return if (lastOffset < headerSize) headerSize else headerSize + payloadBytes.capacity()
  }

  @Synchronized
  fun hasChanges(key: TileKey, firstEpoch: Long, secondEpoch: Long): Boolean {
    if (firstEpoch == secondEpoch) return false
    val history = index[key] ?: return false
    return history.firstAfter(minOf(firstEpoch, secondEpoch)) !=
        history.firstAfter(maxOf(firstEpoch, secondEpoch))
  }

  @Synchronized
  fun reload() {
    channels.forEach(FileChannel::close)
    channels.clear()
    index.clear()
    latestTiles.clear()
    bytes = 0
    records = 0
    latest = 0
    if (!Files.isDirectory(directory)) return
    try {
      Files.list(directory).use { files ->
        files.filter { it.fileName.toString().endsWith(EXTENSION) }.sorted().forEach(::indexSegment)
      }
      index.values.forEach(PackedTileHistory::finishReload)
    } catch (failure: Throwable) {
      channels.forEach(FileChannel::close)
      channels.clear()
      index.clear()
      throw failure
    }
  }

  private fun indexSegment(file: Path) {
    val channel = FileChannel.open(file, StandardOpenOption.READ)
    val segmentId = channels.size
    try {
      val size = channel.size()
      val digest = MessageDigest.getInstance("SHA-256")
      val content = ByteBuffer.allocate(8192)
      var hashed = 0L
      while (hashed < size) {
        content.clear()
        content.limit(minOf(content.capacity().toLong(), size - hashed).toInt())
        val count = channel.read(content, hashed)
        if (count <= 0) throw IOException("Incomplete segment $file")
        digest.update(content.array(), 0, count)
        hashed += count
      }
      val expected = digest.digest().joinToString("") { "%02x".format(it) } + EXTENSION
      if (file.fileName.toString() != expected) throw IOException("Segment hash mismatch: $file")
      if (size < MAGIC.size) throw IOException("Truncated segment $file")
      val magic = ByteBuffer.allocate(MAGIC.size)
      readFully(channel, 0, magic)
      if (!magic.array().contentEquals(MAGIC)) {
        throw IOException("Unsupported segment format $file; delete earlier benchmark data")
      }
      indexAdaptiveSegment(channel, file, size, segmentId)
      bytes += size
      channels += channel
    } catch (failure: Throwable) {
      channel.close()
      throw failure
    }
  }

  private fun indexAdaptiveSegment(channel: FileChannel, file: Path, size: Long, segmentId: Int) {
    if (size < MAGIC.size + Int.SIZE_BYTES) throw IOException("Truncated segment $file")
    val count = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    readFully(channel, MAGIC.size.toLong(), count)
    val groupCount = count.getInt(0)
    if (groupCount < 1 || groupCount > (size - 12) / 17) {
      throw IOException("Invalid tile group count in $file")
    }
    var offset = 12L
    repeat(groupCount) {
      if (offset + 12 > size) throw IOException("Truncated tile group in $file")
      val group = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
      readFully(channel, offset, group)
      val key = TileKey(group.getInt(0), group.getInt(4))
      val recordCount = group.getInt(8)
      offset += 12
      if (recordCount < 1 || recordCount > (size - offset) / 5) {
        throw IOException("Invalid tile record count in $file")
      }
      var previousEpoch = 0L
      val prefix = ByteArray(43)
      repeat(recordCount) { position ->
        val available = minOf(prefix.size.toLong(), size - offset).toInt()
        readFully(channel, offset, ByteBuffer.wrap(prefix, 0, available))
        val length = AdaptiveLayerCodec.recordLength(prefix, available)
        if (length !in 5..AdaptiveLayerCodec.MAX_BYTES || offset + length > size) {
          throw IOException("Invalid adaptive record in $file")
        }
        val body = ByteArray(length)
        val copied = minOf(available, length)
        prefix.copyInto(body, endIndex = copied)
        if (copied < length) {
          readFully(channel, offset + copied, ByteBuffer.wrap(body, copied, length - copied))
        }
        val metadata = AdaptiveLayerCodec.indexMetadata(body, previousEpoch)
        if (position > 0 && metadata.epoch <= previousEpoch) {
          throw IOException("Non-increasing tile epoch in $file")
        }
        addIndexedLayer(key, metadata.epoch, segmentId, offset, length, metadata.coverage)
        previousEpoch = metadata.epoch
        offset += length
      }
    }
    if (offset != size) throw IOException("Unexpected bytes after tile groups in $file")
  }

  private fun addIndexedLayer(
      key: TileKey,
      epoch: Long,
      segmentId: Int,
      offset: Long,
      length: Int,
      coverage: LongArray,
  ) {
    index
        .getOrPut(key, PackedTileHistory::forReload)
        .add(epoch, segmentId, offset, length, coverage)
    records++
    latest = maxOf(latest, epoch)
  }

  private fun readLayer(key: TileKey, history: PackedTileHistory, index: Int): TileLayer {
    val channel = channels[history.segmentAt(index)]
    val body = ByteBuffer.allocate(history.lengthAt(index))
    readFully(channel, history.offsetAt(index), body)
    return AdaptiveLayerCodec.decode(body.array(), key, history.epochAt(index))
  }

  @Synchronized
  override fun close() {
    channels.forEach(FileChannel::close)
    channels.clear()
    latestTiles.clear()
  }

  private companion object {
    val MAGIC = "PALIMPSC".toByteArray(Charsets.US_ASCII)
    const val EXTENSION = ".pseg"

    fun leInt(value: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun write(channel: FileChannel, bytes: ByteArray, digest: MessageDigest) {
      digest.update(bytes)
      val buffer = ByteBuffer.wrap(bytes)
      while (buffer.hasRemaining()) channel.write(buffer)
    }

    fun readFully(channel: FileChannel, position: Long, buffer: ByteBuffer) {
      var offset = position
      while (buffer.hasRemaining()) {
        val count = channel.read(buffer, offset)
        if (count <= 0) throw IOException("Unexpected end of segment")
        offset += count
      }
    }
  }
}
