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
import java.util.zip.CRC32C

/** Immutable, content-addressed segments with a rebuildable in-memory tile/time index. */
class TileHistoryStore(private val directory: Path) : AutoCloseable {
  private data class LayerRef(
      val file: Path,
      val offset: Long,
      val length: Int,
      val epoch: Long,
      val coverage: LongArray,
  )

  data class TileRead(val colors: ByteArray, val layersVisited: Int, val layersDecoded: Int)

  private val index = HashMap<TileKey, MutableList<LayerRef>>()
  private val channels = HashMap<Path, FileChannel>()
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

  val latestEpoch: Long
    @Synchronized get() = latest

  @Synchronized
  fun append(layers: List<TileLayer>) {
    if (layers.isEmpty()) return
    val lastInBatch = HashMap<TileKey, Long>()
    for (layer in layers) {
      val previous = lastInBatch[layer.key] ?: index[layer.key]?.lastOrNull()?.epoch ?: -1L
      require(layer.epoch > previous) { "Tile epochs must increase for ${layer.key}" }
      lastInBatch[layer.key] = layer.epoch
    }
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".palimpsest-", ".tmp")
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      val written = ArrayList<LayerRef>(layers.size)
      var offset = MAGIC.size.toLong()
      FileChannel.open(temporary, StandardOpenOption.WRITE).use { output ->
        write(output, MAGIC, digest)
        for (layer in layers) {
          val body = LayerRecordCodec.encode(layer)
          val checksum = CRC32C().apply { update(body) }.value.toInt()
          val header =
              ByteBuffer.allocate(8)
                  .order(ByteOrder.LITTLE_ENDIAN)
                  .putInt(body.size)
                  .putInt(checksum)
                  .array()
          write(output, header, digest)
          write(output, body, digest)
          written += LayerRef(temporary, offset, body.size, layer.epoch, layer.coverage.copyOf())
          offset += header.size + body.size
        }
        output.force(true)
      }
      val name = digest.digest().joinToString("") { "%02x".format(it) } + EXTENSION
      val sealed = directory.resolve(name)
      if (Files.exists(sealed)) throw IOException("Segment already exists: $sealed")
      Files.move(temporary, sealed, StandardCopyOption.ATOMIC_MOVE)
      val channel = FileChannel.open(sealed, StandardOpenOption.READ)
      channels[sealed] = channel
      for ((position, entry) in written.withIndex()) {
        index.getOrPut(layers[position].key, ::ArrayList).add(entry.copy(file = sealed))
      }
      records += layers.size
      latest = maxOf(latest, layers.maxOf(TileLayer::epoch))
      bytes += offset
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  @Synchronized
  fun read(key: TileKey, epoch: Long): TileRead? {
    val history = index[key] ?: return null
    var left = 0
    var right = history.size
    while (left < right) {
      val middle = (left + right) ushr 1
      if (history[middle].epoch <= epoch) left = middle + 1 else right = middle
    }
    if (left == 0) return null
    val result = ByteArray(TileLayer.PIXELS)
    val missing = LongArray(TileLayer.MASK_WORDS) { -1L }
    var visited = 0
    var decoded = 0
    for (entryIndex in left - 1 downTo 0) {
      val entry = history[entryIndex]
      visited++
      if (entry.coverage.indices.none { entry.coverage[it] and missing[it] != 0L }) continue
      val layer = readLayer(entry)
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
      if (missing.all { it == 0L }) return TileRead(result, visited, decoded)
    }
    throw IOException("Tile $key has no complete initial layer")
  }

  @Synchronized
  fun reload() {
    channels.values.forEach(FileChannel::close)
    channels.clear()
    index.clear()
    bytes = 0
    records = 0
    latest = 0
    if (!Files.isDirectory(directory)) return
    try {
      Files.list(directory).use { files ->
        files.filter { it.fileName.toString().endsWith(EXTENSION) }.sorted().forEach(::indexSegment)
      }
      index.values.forEach { history ->
        history.sortBy(LayerRef::epoch)
        for (position in 1 until history.size) {
          if (history[position - 1].epoch == history[position].epoch) {
            throw IOException("Duplicate tile epoch in sealed segments")
          }
        }
      }
    } catch (failure: Throwable) {
      channels.values.forEach(FileChannel::close)
      channels.clear()
      index.clear()
      throw failure
    }
  }

  private fun indexSegment(file: Path) {
    val channel = FileChannel.open(file, StandardOpenOption.READ)
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
      if (!magic.array().contentEquals(MAGIC)) throw IOException("Unsupported segment format $file")
      var offset = MAGIC.size.toLong()
      while (offset < size) {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        readFully(channel, offset, header)
        val length = header.getInt(0)
        val checksum = header.getInt(4)
        if (
            length !in (LayerRecordCodec.FIXED_BYTES + 1)..LayerRecordCodec.MAX_BYTES ||
                offset + 8 + length > size
        )
            throw IOException("Invalid record in $file")
        val body = ByteBuffer.allocate(length)
        readFully(channel, offset + 8, body)
        if (CRC32C().apply { update(body.array()) }.value.toInt() != checksum) {
          throw IOException("Record checksum mismatch in $file")
        }
        val layer = LayerRecordCodec.decode(body.array())
        index
            .getOrPut(layer.key, ::ArrayList)
            .add(LayerRef(file, offset, length, layer.epoch, layer.coverage))
        records++
        latest = maxOf(latest, layer.epoch)
        offset += 8 + length
      }
      bytes += size
      channels[file] = channel
    } catch (failure: Throwable) {
      channel.close()
      throw failure
    }
  }

  private fun readLayer(entry: LayerRef): TileLayer {
    val channel = channels[entry.file] ?: throw IOException("Missing segment ${entry.file}")
    val body = ByteBuffer.allocate(entry.length)
    readFully(channel, entry.offset + 8, body)
    return LayerRecordCodec.decode(body.array())
  }

  @Synchronized
  override fun close() {
    channels.values.forEach(FileChannel::close)
    channels.clear()
  }

  private companion object {
    val MAGIC = "PALIMP01".toByteArray(Charsets.US_ASCII)
    const val EXTENSION = ".pseg"

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
