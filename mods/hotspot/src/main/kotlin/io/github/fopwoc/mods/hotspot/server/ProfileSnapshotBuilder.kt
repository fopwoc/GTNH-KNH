package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.hotspot.protocol.ChunkProfile
import io.github.fopwoc.mods.hotspot.protocol.DimensionProfile
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshot
import io.github.fopwoc.mods.hotspot.protocol.TileEntityProfile
import io.github.fopwoc.mods.hotspot.server.profiler.RawProfile
import io.github.fopwoc.mods.hotspot.server.profiler.RawTileEntitySample

/** Groups raw samples into dimensions → chunks → heaviest tile entities. Pure; no world access. */
object ProfileSnapshotBuilder {
  data class Limits(
      val minNanosPerTileEntity: Double,
      val maxListedTileEntitiesPerChunk: Int,
  )

  fun build(
      raw: RawProfile,
      requestId: Long,
      takenAtEpochMillis: Long,
      durationTicks: Int,
      limits: Limits,
      dimensionName: (Int) -> String,
      tileEntityName: (RawTileEntitySample) -> String,
  ): ProfileSnapshot {
    val dimensionIds =
        (raw.tileEntities.map { it.dimensionId } +
                raw.entities.map { it.dimensionId } +
                raw.dimensionTickNanos.keys)
            .toSortedSet()

    val dimensions = dimensionIds.map { dimensionId ->
      val chunkTileEntities =
          raw.tileEntities
              .filter { it.dimensionId == dimensionId }
              .groupBy { ChunkKey(it.x shr 4, it.z shr 4) }
      val chunkEntities =
          raw.entities
              .filter { it.dimensionId == dimensionId }
              .groupBy { ChunkKey(it.chunkX, it.chunkZ) }

      val chunks =
          (chunkTileEntities.keys + chunkEntities.keys).map { key ->
            val tileEntities = chunkTileEntities[key].orEmpty()
            val entities = chunkEntities[key].orEmpty()
            ChunkProfile(
                chunkX = key.x,
                chunkZ = key.z,
                tileEntityMs = tileEntities.sumOf { it.nanosPerTick } / NANOS_PER_MILLI,
                entityMs = entities.sumOf { it.nanosPerTick } / NANOS_PER_MILLI,
                tileEntityCount = tileEntities.size,
                entityCount = entities.size,
                tileEntities =
                    tileEntities
                        .filter { it.nanosPerTick >= limits.minNanosPerTileEntity }
                        .sortedByDescending { it.nanosPerTick }
                        .take(limits.maxListedTileEntitiesPerChunk)
                        .map { sample ->
                          TileEntityProfile(
                              x = sample.x,
                              y = sample.y,
                              z = sample.z,
                              ms = sample.nanosPerTick / NANOS_PER_MILLI,
                              name = tileEntityName(sample),
                              className = sample.className,
                          )
                        },
            )
          }

      DimensionProfile(
          id = dimensionId,
          name = dimensionName(dimensionId),
          tickMs = (raw.dimensionTickNanos[dimensionId] ?: 0.0) / NANOS_PER_MILLI,
          chunks = chunks.sortedByDescending { it.totalMs },
      )
    }

    return ProfileSnapshot(
        requestId = requestId,
        takenAtEpochMillis = takenAtEpochMillis,
        durationTicks = durationTicks,
        dimensions = dimensions.sortedByDescending { it.tickMs },
    )
  }

  private data class ChunkKey(val x: Int, val z: Int)

  private const val NANOS_PER_MILLI = 1_000_000.0
}
