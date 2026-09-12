package io.github.fopwoc.mods.hotspot.protocol

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import io.netty.buffer.ByteBuf

/**
 * Server → client page of a snapshot. Names are sent once per page as a table and referenced by
 * index, since a chunk full of cables repeats the same two strings hundreds of times.
 *
 * Decoding never throws; anything malformed leaves [part] null.
 */
class ProfileSnapshotPartMessage() : IMessage {
  var part: ProfileSnapshotPart? = null
    private set

  constructor(part: ProfileSnapshotPart) : this() {
    this.part = part
  }

  override fun fromBytes(buffer: ByteBuf) {
    part = null
    if (buffer.readableBytes() < Int.SIZE_BYTES || buffer.readInt() != HOTSPOT_PROTOCOL_VERSION) {
      return
    }
    if (buffer.readableBytes() < HEADER_BYTES) {
      return
    }
    val requestId = buffer.readLong()
    val takenAt = buffer.readLong()
    val durationTicks = buffer.readInt()
    val partIndex = buffer.readInt()
    val flags = buffer.readUnsignedByte().toInt()
    val isLast = flags and FLAG_LAST != 0
    val dimension = if (flags and FLAG_DIMENSION != 0) buffer.readDimension() ?: return else null
    part = ProfileSnapshotPart(requestId, takenAt, durationTicks, partIndex, isLast, dimension)
  }

  override fun toBytes(buffer: ByteBuf) {
    val part = checkNotNull(part) { "Cannot encode an invalid snapshot part" }
    buffer.writeInt(HOTSPOT_PROTOCOL_VERSION)
    buffer.writeLong(part.requestId)
    buffer.writeLong(part.takenAtEpochMillis)
    buffer.writeInt(part.durationTicks)
    buffer.writeInt(part.partIndex)
    var flags = 0
    if (part.isLast) flags = flags or FLAG_LAST
    if (part.dimension != null) flags = flags or FLAG_DIMENSION
    buffer.writeByte(flags)
    part.dimension?.let { buffer.writeDimension(it) }
  }

  private fun ByteBuf.readDimension(): DimensionPage? {
    if (readableBytes() < Int.SIZE_BYTES) return null
    val id = readInt()
    val name = readBoundedUtf8() ?: return null
    if (readableBytes() < Long.SIZE_BYTES + Short.SIZE_BYTES) return null
    val tickMs = readDouble()
    val nameCount = readUnsignedShort()
    if (nameCount > MAX_NAMES_PER_PART) return null
    val names = ArrayList<String>(nameCount)
    repeat(nameCount) { names += readBoundedUtf8() ?: return null }
    if (readableBytes() < Short.SIZE_BYTES) return null
    val chunkCount = readUnsignedShort()
    if (chunkCount > MAX_CHUNKS_PER_PART) return null
    val chunks = ArrayList<ChunkProfile>(chunkCount)
    repeat(chunkCount) {
      if (readableBytes() < CHUNK_HEADER_BYTES) return null
      val chunkX = readInt()
      val chunkZ = readInt()
      val tileEntityMs = readFloat().toDouble()
      val entityMs = readFloat().toDouble()
      val tileEntityCount = readInt()
      val entityCount = readInt()
      val listed = readUnsignedShort()
      if (listed > MAX_TILE_ENTITIES_PER_CHUNK || readableBytes() < listed * TILE_ENTITY_BYTES) {
        return null
      }
      val tileEntities = ArrayList<TileEntityProfile>(listed)
      repeat(listed) {
        val x = readInt()
        val y = readShort().toInt()
        val z = readInt()
        val ms = readFloat().toDouble()
        val nameIndex = readUnsignedShort()
        val classIndex = readUnsignedShort()
        if (nameIndex >= names.size || classIndex >= names.size) return null
        tileEntities += TileEntityProfile(x, y, z, ms, names[nameIndex], names[classIndex])
      }
      chunks +=
          ChunkProfile(
              chunkX = chunkX,
              chunkZ = chunkZ,
              tileEntityMs = tileEntityMs,
              entityMs = entityMs,
              tileEntityCount = tileEntityCount,
              entityCount = entityCount,
              tileEntities = tileEntities,
          )
    }
    return DimensionPage(id, name, tickMs, chunks)
  }

  private fun ByteBuf.writeDimension(page: DimensionPage) {
    writeInt(page.id)
    ByteBufUtils.writeUTF8String(this, page.name.take(MAX_NAME_LENGTH))
    writeDouble(page.tickMs)

    val names = LinkedHashMap<String, Int>()
    fun index(name: String): Int =
        names
            .getOrPut(name.take(MAX_NAME_LENGTH)) { names.size }
            .also {
              check(names.size <= MAX_NAMES_PER_PART) { "Too many names in one snapshot part" }
            }
    val chunks = page.chunks.take(MAX_CHUNKS_PER_PART)
    val encodedChunks = chunks.map { chunk ->
      chunk.tileEntities.take(MAX_TILE_ENTITIES_PER_CHUNK).map { entry ->
        Triple(entry, index(entry.name), index(entry.className))
      }
    }
    writeShort(names.size)
    names.keys.forEach { ByteBufUtils.writeUTF8String(this, it) }
    writeShort(chunks.size)
    chunks.forEachIndexed { chunkIndex, chunk ->
      writeInt(chunk.chunkX)
      writeInt(chunk.chunkZ)
      writeFloat(chunk.tileEntityMs.toFloat())
      writeFloat(chunk.entityMs.toFloat())
      writeInt(chunk.tileEntityCount)
      writeInt(chunk.entityCount)
      val entries = encodedChunks[chunkIndex]
      writeShort(entries.size)
      entries.forEach { (entry, nameIndex, classIndex) ->
        writeInt(entry.x)
        writeShort(entry.y)
        writeInt(entry.z)
        writeFloat(entry.ms.toFloat())
        writeShort(nameIndex)
        writeShort(classIndex)
      }
    }
  }

  /** Mirrors [ByteBufUtils.readUTF8String] (varint length + UTF-8) with a hard size cap. */
  private fun ByteBuf.readBoundedUtf8(): String? {
    if (readableBytes() < 1) return null
    val length = ByteBufUtils.readVarInt(this, 2)
    if (length < 0 || length > MAX_NAME_LENGTH * 3 || readableBytes() < length) {
      return null
    }
    val bytes = ByteArray(length)
    readBytes(bytes)
    return String(bytes, Charsets.UTF_8)
  }

  private companion object {
    const val FLAG_LAST = 1
    const val FLAG_DIMENSION = 2
    const val HEADER_BYTES = Long.SIZE_BYTES * 2 + Int.SIZE_BYTES * 2 + 1
    const val CHUNK_HEADER_BYTES = Int.SIZE_BYTES * 4 + Float.SIZE_BYTES * 2 + Short.SIZE_BYTES
    const val TILE_ENTITY_BYTES = Int.SIZE_BYTES * 2 + Short.SIZE_BYTES * 3 + Float.SIZE_BYTES
  }
}
