package io.github.fopwoc.mods.tabtps.server.sampling

import io.github.fopwoc.mods.framework.server.ServerAccess
import io.github.fopwoc.mods.tabtps.protocol.DimensionTpsMetrics
import io.github.fopwoc.mods.tabtps.protocol.MAX_DIMENSIONS_PER_SNAPSHOT
import io.github.fopwoc.mods.tabtps.protocol.TpsMetrics
import io.github.fopwoc.mods.tabtps.protocol.TpsRequest
import io.github.fopwoc.mods.tabtps.protocol.TpsSnapshot
import net.minecraft.server.level.ServerPlayer

object ModernTpsSampler {
    fun sample(
        player: ServerPlayer,
        request: TpsRequest,
        serverAccess: ServerAccess,
    ): TpsSnapshot {
        val server = player.level().server
        val serverTimes = server.tickTimesNanos
        val index = Math.floorMod(server.tickCount, serverTimes.size)
        val serverMspt = RollingTickWindow.averageMilliseconds(serverTimes, index) ?: 0.0
        val serverMetrics = TpsMetrics(RollingTickWindow.tpsFor(serverMspt), serverMspt)
        val levels = server.allLevels.associateBy { it.dimension().identifier().toString() }
        return TpsSnapshot(
            requestId = request.requestId,
            server = serverMetrics,
            currentDimensionId = player.level().dimension().identifier().toString(),
            dimensions =
                request.dimensionIds.distinct().take(MAX_DIMENSIONS_PER_SNAPSHOT).mapNotNull { id ->
                    val level = levels[id] ?: return@mapNotNull null
                    val samples = serverAccess.worldTickTimes(level) ?: return@mapNotNull null
                    val mspt =
                        RollingTickWindow.averageMilliseconds(
                            samples.durationsNanos,
                            samples.lastIndex,
                        ) ?: 0.0
                    DimensionTpsMetrics(id, id, TpsMetrics(serverMetrics.tps, mspt))
                },
        )
    }
}
