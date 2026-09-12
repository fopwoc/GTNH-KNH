package io.github.fopwoc.mods.hotspot.server

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import io.github.fopwoc.mods.hotspot.protocol.ProfileRequest
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotPartMessage
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotParts
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatus
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatusMessage
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatusUpdate
import io.github.fopwoc.mods.hotspot.server.profiler.OpisAvailability
import io.github.fopwoc.mods.hotspot.server.profiler.OpisTickProfiler
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.common.DimensionManager
import org.apache.logging.log4j.LogManager

/**
 * One profiling run at a time. A request while a run is active joins it, so several players asking
 * at once share one window instead of restarting the profiler under each other. Everything runs on
 * the server thread: requests arrive there and the tick event finishes the run.
 */
object ProfilingService {
  private val logger = LogManager.getLogger(ProfilingService::class.java)

  private class Run(var ticksLeft: Int, val totalTicks: Int) {
    val requesters = LinkedHashMap<EntityPlayerMP, Long>()
  }

  private var run: Run? = null

  fun handle(player: EntityPlayerMP, request: ProfileRequest) {
    if (!HotspotAccess.isAllowed(player)) {
      logger.info("Denied profiling request from {}", player.commandSenderName)
      sendStatus(player, request.requestId, ProfileStatus.DENIED, 0)
      return
    }
    if (!OpisAvailability.isPresent) {
      sendStatus(player, request.requestId, ProfileStatus.PROFILER_UNAVAILABLE, 0)
      return
    }

    val current = run ?: startRun(request.durationTicks, player)
    current.requesters[player] = request.requestId
    sendStatus(player, request.requestId, ProfileStatus.STARTED, current.ticksLeft)
  }

  @SubscribeEvent
  fun onServerTick(event: TickEvent.ServerTickEvent) {
    if (event.phase != TickEvent.Phase.END) {
      return
    }
    val current = run ?: return
    current.ticksLeft -= 1
    if (current.ticksLeft > 0) {
      return
    }
    run = null
    finish(current)
  }

  private fun startRun(requestedTicks: Int, player: EntityPlayerMP): Run {
    val ticks =
        requestedTicks.coerceIn(1, HotspotServerConfig.maxDurationSeconds * TICKS_PER_SECOND)
    OpisTickProfiler.start()
    logger.info("Profiling for {} ticks, requested by {}", ticks, player.commandSenderName)
    return Run(ticksLeft = ticks, totalTicks = ticks).also { run = it }
  }

  fun shutdown() {
    if (run != null) {
      run = null
      OpisTickProfiler.stop()
    }
  }

  private fun finish(current: Run) {
    val raw = OpisTickProfiler.collect(current.totalTicks)
    OpisTickProfiler.stop()

    val snapshot =
        ProfileSnapshotBuilder.build(
            raw = raw,
            requestId = 0,
            takenAtEpochMillis = System.currentTimeMillis(),
            durationTicks = current.totalTicks,
            limits =
                ProfileSnapshotBuilder.Limits(
                    minNanosPerTileEntity = HotspotServerConfig.minMicrosPerTileEntity * 1_000.0,
                    maxListedTileEntitiesPerChunk =
                        HotspotServerConfig.maxListedTileEntitiesPerChunk,
                ),
            dimensionName = { id ->
              DimensionManager.getWorld(id)?.provider?.dimensionName ?: "DIM$id"
            },
            tileEntityName = TileEntityNameResolver::resolve,
        )
    val parts = ProfileSnapshotParts.split(snapshot)
    logger.info(
        "Profiled {} tile entities across {} dimensions; {} parts",
        raw.tileEntities.size,
        snapshot.dimensions.size,
        parts.size,
    )

    current.requesters.forEach { (player, requestId) ->
      if (player.playerNetServerHandler?.netManager?.isChannelOpen != true) {
        return@forEach
      }
      parts.forEach { part ->
        HotspotChannel.parts.send(
            player,
            ProfileSnapshotPartMessage(part.copy(requestId = requestId)),
        )
      }
    }
  }

  private fun sendStatus(
      player: EntityPlayerMP,
      requestId: Long,
      status: ProfileStatus,
      ticks: Int,
  ) {
    val maxTicks = HotspotServerConfig.maxDurationSeconds * TICKS_PER_SECOND
    HotspotChannel.statuses.send(
        player,
        ProfileStatusMessage(ProfileStatusUpdate(requestId, status, ticks, maxTicks)),
    )
  }

  private const val TICKS_PER_SECOND = 20
}
