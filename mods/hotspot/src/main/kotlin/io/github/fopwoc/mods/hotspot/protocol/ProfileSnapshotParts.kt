package io.github.fopwoc.mods.hotspot.protocol

/** Splits a snapshot into pages that encode below [maxBytes] and reassembles them. */
object ProfileSnapshotParts {
  fun split(snapshot: ProfileSnapshot, maxBytes: Int = MAX_PART_BYTES): List<ProfileSnapshotPart> {
    val parts = mutableListOf<ProfileSnapshotPart>()
    fun emit(dimension: DimensionPage?) {
      parts +=
          ProfileSnapshotPart(
              requestId = snapshot.requestId,
              takenAtEpochMillis = snapshot.takenAtEpochMillis,
              durationTicks = snapshot.durationTicks,
              partIndex = parts.size,
              isLast = false,
              dimension = dimension,
          )
    }

    for (dimension in snapshot.dimensions) {
      var page = mutableListOf<ChunkProfile>()
      var pageBytes = DIMENSION_HEADER_BYTES + dimension.name.length * 3
      val pageNames = HashSet<String>()
      for (chunk in dimension.chunks) {
        val trimmed = chunk.trimmedForWire()
        val chunkBytes = trimmed.wireBytes(pageNames)
        if (
            page.isNotEmpty() && pageBytes + chunkBytes > maxBytes ||
                page.size >= MAX_CHUNKS_PER_PART
        ) {
          emit(DimensionPage(dimension.id, dimension.name, dimension.tickMs, page))
          page = mutableListOf()
          pageNames.clear()
          pageBytes = DIMENSION_HEADER_BYTES + dimension.name.length * 3
        }
        page += trimmed
        pageBytes += trimmed.wireBytes(pageNames)
        trimmed.tileEntities.forEach {
          pageNames += it.name
          pageNames += it.className
        }
      }
      emit(DimensionPage(dimension.id, dimension.name, dimension.tickMs, page))
    }
    if (parts.isEmpty()) {
      emit(null)
    }
    parts[parts.lastIndex] = parts.last().copy(isLast = true)
    return parts
  }

  private fun ChunkProfile.trimmedForWire(): ChunkProfile =
      if (tileEntities.size <= MAX_TILE_ENTITIES_PER_CHUNK) this
      else copy(tileEntities = tileEntities.take(MAX_TILE_ENTITIES_PER_CHUNK))

  /** Upper-bound estimate; names already in [knownNames] cost only their index. */
  private fun ChunkProfile.wireBytes(knownNames: Set<String>): Int {
    var bytes = CHUNK_HEADER_BYTES + tileEntities.size * TILE_ENTITY_BYTES
    val newNames = HashSet<String>()
    tileEntities.forEach { entry ->
      if (entry.name !in knownNames) newNames += entry.name
      if (entry.className !in knownNames) newNames += entry.className
    }
    newNames.forEach { bytes += 2 + minOf(it.length, MAX_NAME_LENGTH) * 3 }
    return bytes
  }

  /** Collects pages of one request; pages of other requests are dropped. */
  class Assembler {
    private var requestId: Long? = null
    private val dimensions = LinkedHashMap<Int, MutableList<DimensionPage>>()

    fun reset() {
      requestId = null
      dimensions.clear()
    }

    /** Returns the complete snapshot when [part] was its last page. */
    fun accept(part: ProfileSnapshotPart, expectedRequestId: Long): ProfileSnapshot? {
      if (part.requestId != expectedRequestId) {
        return null
      }
      if (requestId != part.requestId) {
        reset()
        requestId = part.requestId
      }
      part.dimension?.let { page -> dimensions.getOrPut(page.id) { mutableListOf() } += page }
      if (!part.isLast) {
        return null
      }
      val snapshot =
          ProfileSnapshot(
              requestId = part.requestId,
              takenAtEpochMillis = part.takenAtEpochMillis,
              durationTicks = part.durationTicks,
              dimensions =
                  dimensions.values.map { pages ->
                    val first = pages.first()
                    DimensionProfile(
                        id = first.id,
                        name = first.name,
                        tickMs = first.tickMs,
                        chunks = pages.flatMap(DimensionPage::chunks),
                    )
                  },
          )
      reset()
      return snapshot
    }
  }

  private const val DIMENSION_HEADER_BYTES = 4 + 8 + 2 + 2
  private const val CHUNK_HEADER_BYTES = 4 + 4 + 4 + 4 + 4 + 4 + 2
  private const val TILE_ENTITY_BYTES = 4 + 2 + 4 + 4 + 2 + 2
}
