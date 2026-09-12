package io.github.fopwoc.mods.hotspot.server.profiler

import cpw.mods.fml.relauncher.Side
import mcp.mobius.mobiuscore.profiler.ProfilerSection
import mcp.mobius.opis.data.profilers.ProfilerDimTick
import mcp.mobius.opis.data.profilers.ProfilerEntityUpdate
import mcp.mobius.opis.data.profilers.ProfilerTileEntityUpdate

/**
 * Drives MobiusCore's ASM-injected profiler (installed by Opis at server start) without going
 * through Opis' own commands or permission checks.
 *
 * Only touch this object after [OpisAvailability] said yes: its methods reference Opis classes, so
 * loading it on a server without Opis throws. `activateAll` swaps the real profilers in, `reset`
 * clears both the active and the suspended ones, and `desactivateAll` swaps dummies back — read the
 * data while still active.
 */
object OpisTickProfiler {
  fun start() {
    ProfilerSection.resetAll(Side.SERVER)
    ProfilerSection.activateAll(Side.SERVER)
  }

  fun stop() {
    ProfilerSection.desactivateAll(Side.SERVER)
  }

  /** [ticks] is how many server ticks ran while active; sums are divided by it. */
  fun collect(ticks: Int): RawProfile {
    val divisor = ticks.coerceAtLeast(1).toDouble()

    val tileEntities =
        (ProfilerSection.TILEENT_UPDATETIME.profiler as? ProfilerTileEntityUpdate)?.let { profiler
          ->
          profiler.data.entries.map { (position, stats) ->
            RawTileEntitySample(
                dimensionId = position.dim,
                x = position.x,
                y = position.y,
                z = position.z,
                nanosPerTick = stats.sum / divisor,
                className = profiler.refs[position]?.simpleName ?: "",
            )
          }
        } ?: emptyList()

    val entities =
        (ProfilerSection.ENTITY_UPDATETIME.profiler as? ProfilerEntityUpdate)?.let { profiler ->
          profiler.data.entries.mapNotNull { (entity, stats) ->
            val world = entity?.worldObj ?: return@mapNotNull null
            RawEntitySample(
                dimensionId = world.provider.dimensionId,
                chunkX = entity.chunkCoordX,
                chunkZ = entity.chunkCoordZ,
                nanosPerTick = stats.sum / divisor,
            )
          }
        } ?: emptyList()

    val dimensionTicks =
        (ProfilerSection.DIMENSION_TICK.profiler as? ProfilerDimTick)?.data?.mapValues { (_, stats)
          ->
          stats.sum / divisor
        } ?: emptyMap()

    return RawProfile(tileEntities, entities, dimensionTicks)
  }
}
