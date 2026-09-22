package io.github.fopwoc.mods.hotspot.server

import io.github.fopwoc.mods.framework.event.ServerEvents
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.hotspot.config.HotspotServerConfig
import io.github.fopwoc.mods.hotspot.protocol.AccessCheck
import io.github.fopwoc.mods.hotspot.protocol.AccessReply
import io.github.fopwoc.mods.hotspot.protocol.HotspotChannel
import io.github.fopwoc.mods.hotspot.protocol.ProfileRequest
import io.github.fopwoc.mods.hotspot.protocol.ProfileSnapshotParts
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatus
import io.github.fopwoc.mods.hotspot.protocol.ProfileStatusUpdate
import io.github.fopwoc.mods.hotspot.server.profiler.OpisAvailability
import io.github.fopwoc.mods.hotspot.server.profiler.OpisTickProfiler
import io.github.fopwoc.mods.framework.player.GamePlayer
import net.minecraftforge.common.DimensionManager

/**
 * One profiling run at a time. A request while a run is active joins it, so several players asking
 * at once share one window instead of restarting the profiler under each other. Everything runs on
 * the server thread: requests arrive there and the tick event finishes the run.
 */
object ProfilingService {
    private val logger = logger<ProfilingService>()

    private class Run(var ticksLeft: Int, val totalTicks: Int) {
        val requesters = LinkedHashMap<GamePlayer, Long>()
    }

    private var run: Run? = null

    /**
     * Answers the menu's "may I?" so the client can show a clear message before anyone profiles.
     */
    fun answerAccessCheck(player: GamePlayer, check: AccessCheck) {
        HotspotChannel.accessReplies.send(
            player,
            AccessReply(
                nonce = check.nonce,
                allowed = HotspotAccess.isAllowed(player),
                profilerAvailable = OpisAvailability.isPresent,
                maxDurationTicks = HotspotServerConfig.maxDurationSeconds * TICKS_PER_SECOND,
            ),
        )
    }

    fun handle(player: GamePlayer, request: ProfileRequest) {
        if (!HotspotAccess.isAllowed(player)) {
            logger.info("Denied profiling request from {}", player.name)
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

    fun install() {
        ServerEvents.tickEnd.subscribe { tick() }
        ServerEvents.stopping.subscribe { shutdown() }
    }

    private fun tick() {
        val current = run ?: return
        current.ticksLeft -= 1
        if (current.ticksLeft > 0) {
            return
        }
        run = null
        finish(current)
    }

    private fun startRun(requestedTicks: Int, player: GamePlayer): Run {
        val ticks =
            requestedTicks.coerceIn(1, HotspotServerConfig.maxDurationSeconds * TICKS_PER_SECOND)
        OpisTickProfiler.start()
        logger.info("Profiling for {} ticks, requested by {}", ticks, player.name)
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
                        minNanosPerTileEntity =
                            HotspotServerConfig.minMicrosPerTileEntity * 1_000.0,
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
            parts.forEach { part -> HotspotChannel.parts.send(player, part.copy(requestId = requestId)) }
        }
    }

    private fun sendStatus(
        player: GamePlayer,
        requestId: Long,
        status: ProfileStatus,
        ticks: Int,
    ) {
        val maxTicks = HotspotServerConfig.maxDurationSeconds * TICKS_PER_SECOND
        HotspotChannel.statuses.send(
            player,
            ProfileStatusUpdate(requestId, status, ticks, maxTicks),
        )
    }

    private const val TICKS_PER_SECOND = 20
}
