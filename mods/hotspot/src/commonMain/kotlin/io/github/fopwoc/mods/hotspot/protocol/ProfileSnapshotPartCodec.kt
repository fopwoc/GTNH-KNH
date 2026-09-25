package io.github.fopwoc.mods.hotspot.protocol

import io.github.fopwoc.mods.framework.network.MessageCodec
import io.github.fopwoc.mods.framework.network.MessageReader
import io.github.fopwoc.mods.framework.network.MessageWriter

/**
 * Server → client page of a snapshot. Names are sent once per page as a table and referenced by
 * index, since a chunk full of cables repeats the same two strings hundreds of times.
 */
object ProfileSnapshotPartCodec : MessageCodec<ProfileSnapshotPart> {

    override fun encode(writer: MessageWriter, payload: ProfileSnapshotPart) {
        writer.long(payload.requestId)
        writer.long(payload.takenAtEpochMillis)
        writer.int(payload.durationTicks)
        writer.int(payload.partIndex)
        var flags = 0
        if (payload.isLast) flags = flags or FLAG_LAST
        if (payload.dimension != null) flags = flags or FLAG_DIMENSION
        writer.byte(flags)
        payload.dimension?.let { writer.writeDimension(it) }
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

    private fun MessageWriter.writeDimension(page: DimensionPage) {
        int(page.id)
        utf8(page.name, MAX_NAME_LENGTH)
        double(page.tickMs)

        val names = LinkedHashMap<String, Int>()
        fun index(name: String): Int =
            names
                .getOrPut(name.take(MAX_NAME_LENGTH)) { names.size }
                .also {
                    check(names.size <= MAX_NAMES_PER_PART) {
                        "Too many names in one snapshot part"
                    }
                }
        val chunks = page.chunks.take(MAX_CHUNKS_PER_PART)
        val encodedChunks = chunks.map { chunk ->
            chunk.tileEntities.take(MAX_TILE_ENTITIES_PER_CHUNK).map { entry ->
                Triple(entry, index(entry.name), index(entry.className))
            }
        }
        short(names.size)
        names.keys.forEach { utf8(it, MAX_NAME_LENGTH) }
        short(chunks.size)
        chunks.forEachIndexed { chunkIndex, chunk ->
            int(chunk.chunkX)
            int(chunk.chunkZ)
            float(chunk.tileEntityMs.toFloat())
            float(chunk.entityMs.toFloat())
            int(chunk.tileEntityCount)
            int(chunk.entityCount)
            val entries = encodedChunks[chunkIndex]
            short(entries.size)
            entries.forEach { (entry, nameIndex, classIndex) ->
                int(entry.x)
                short(entry.y)
                int(entry.z)
                float(entry.ms.toFloat())
                short(nameIndex)
                short(classIndex)
            }
        }
    }

    private const val FLAG_LAST = 1
    private const val FLAG_DIMENSION = 2
}
