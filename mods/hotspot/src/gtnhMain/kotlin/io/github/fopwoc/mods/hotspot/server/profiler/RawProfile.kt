package io.github.fopwoc.mods.hotspot.server.profiler

/** What a profiling run measured, before names and grouping. Nanos are per server tick. */
data class RawProfile(
    val tileEntities: List<RawTileEntitySample>,
    val entities: List<RawEntitySample>,
    /** Mean full-tick time per dimension; missing when the profiler did not see the world tick. */
    val dimensionTickNanos: Map<Int, Double>,
)

data class RawTileEntitySample(
    val dimensionId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val nanosPerTick: Double,
    val className: String,
)

data class RawEntitySample(
    val dimensionId: Int,
    val chunkX: Int,
    val chunkZ: Int,
    val nanosPerTick: Double,
)
