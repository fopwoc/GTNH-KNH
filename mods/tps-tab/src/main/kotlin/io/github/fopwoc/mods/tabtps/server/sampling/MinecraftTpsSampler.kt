package io.github.fopwoc.mods.tabtps.server.sampling

import io.github.fopwoc.mods.tabtps.protocol.DimensionTpsMetrics
import io.github.fopwoc.mods.tabtps.protocol.MAX_DIMENSIONS_PER_SNAPSHOT
import io.github.fopwoc.mods.tabtps.protocol.MAX_DIMENSION_NAME_LENGTH
import io.github.fopwoc.mods.tabtps.protocol.TpsMetrics
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot
import net.minecraft.server.MinecraftServer
import net.minecraftforge.common.DimensionManager

object MinecraftTpsSampler {
  fun sample(
      server: MinecraftServer,
      requestId: Long,
      currentDimensionId: Int,
      dimensionIds: List<Int>,
  ): TpsSnapshot {
    val currentIndex = Math.floorMod(server.tickCounter, server.tickTimeArray.size)
    val serverMspt =
        RollingTickWindow.averageMilliseconds(server.tickTimeArray, currentIndex) ?: 0.0
    val serverMetrics = TpsMetrics(RollingTickWindow.tpsFor(serverMspt), serverMspt)
    return TpsSnapshot(
        requestId = requestId,
        server = serverMetrics,
        currentDimensionId = currentDimensionId,
        dimensions =
            dimensionIds.distinct().take(MAX_DIMENSIONS_PER_SNAPSHOT).mapNotNull { dimensionId ->
              val world = DimensionManager.getWorld(dimensionId) ?: return@mapNotNull null
              val samples = server.worldTickTimes[dimensionId] ?: return@mapNotNull null
              val mspt = RollingTickWindow.averageMilliseconds(samples, currentIndex) ?: 0.0
              DimensionTpsMetrics(
                  dimensionId = dimensionId,
                  dimensionName =
                      (world.provider.dimensionName ?: "Dimension $dimensionId").take(
                          MAX_DIMENSION_NAME_LENGTH
                      ),
                  metrics = TpsMetrics(serverMetrics.tps, mspt),
              )
            },
    )
  }
}
