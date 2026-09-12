package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.VersionedMessage
import io.github.fopwoc.mods.framework.network.writeUtf8
import io.netty.buffer.ByteBuf

/**
 * Server → client page of a snapshot. Names are sent once per page as a table and referenced by
 * index, since a chunk full of cables repeats the same two strings hundreds of times.
 */
class ProfileSnapshotPartMessage() :
    VersionedMessage<ProfileSnapshotPart>(HOTSPOT_PROTOCOL_VERSION) {
  constructor(part: ProfileSnapshotPart) : this() {
    payload = part
  }

  override fun encode(buffer: ByteBuf, payload: ProfileSnapshotPart) {
    buffer.writeLong(payload.requestId)
    buffer.writeLong(payload.takenAtEpochMillis)
    buffer.writeInt(payload.durationTicks)
    buffer.writeInt(payload.partIndex)
    var flags = 0
    if (payload.isLast) flags = flags or FLAG_LAST
    if (payload.dimension != null) flags = flags or FLAG_DIMENSION
    buffer.writeByte(flags)
    payload.dimension?.let { buffer.writeDimension(it) }
  }

  override fun decode(reader: MessageReader): ProfileSnapshotPart {
    val requestId = reader.long()
    val takenAt = reader.long()
    val durationTicks = reader.int()
    val partIndex = reader.int()
    val flags = reader.unsignedByte()
    val dimension = if (flags and FLAG_DIMENSION != 0) reader.readDimension() else null
    return ProfileSnapshotPart(
        requestId = requestId,
        takenAtEpochMillis = takenAt,
        durationTicks = durationTicks,
        partIndex = partIndex,
        isLast = flags and FLAG_LAST != 0,
        dimension = dimension,
    )
  }

  private fun MessageReader.readDimension(): DimensionPage {
    val id = int()
    val name = utf8(MAX_NAME_LENGTH)
    val tickMs = double()
    val names = list(MAX_NAMES_PER_PART, { unsignedShort() }) { utf8(MAX_NAME_LENGTH) }
    val chunks =
        list(MAX_CHUNKS_PER_PART, { unsignedShort() }) {
          val chunkX = int()
          val chunkZ = int()
          val tileEntityMs = float().toDouble()
          val entityMs = float().toDouble()
          val tileEntityCount = int()
          val entityCount = int()
          val tileEntities =
              list(MAX_TILE_ENTITIES_PER_CHUNK, { unsignedShort() }) {
                val x = int()
                val y = short()
                val z = int()
                val ms = float().toDouble()
                val nameIndex = unsignedShort()
                val classIndex = unsignedShort()
                check(nameIndex < names.size && classIndex < names.size) {
                  "Name index out of table"
                }
                TileEntityProfile(x, y, z, ms, names[nameIndex], names[classIndex])
              }
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
    writeUtf8(page.name, MAX_NAME_LENGTH)
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
    names.keys.forEach { writeUtf8(it, MAX_NAME_LENGTH) }
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

  private companion object {
    const val FLAG_LAST = 1
    const val FLAG_DIMENSION = 2
  }
}
