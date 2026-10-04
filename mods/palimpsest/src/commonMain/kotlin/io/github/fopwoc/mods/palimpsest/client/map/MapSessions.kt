package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.platform.Platform
import io.github.fopwoc.mods.palimpsest.client.waypoint.WaypointCamera
import io.github.fopwoc.mods.palimpsest.config.PalimpsestConfig
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.history.WorldHistory
import io.github.fopwoc.palimpsest.db.DimensionId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Opens a [MapSession] for whatever world and dimension the client is in, ticks it, and closes it
 * when the world goes away or the dimension changes.
 */
// The map must never take the client down: every failure is logged and the session dropped.
@Suppress("TooGenericExceptionCaught")
object MapSessions {
    private val logger = logger<MapSessions>()
    private lateinit var platform: MapPlatform
    private var current: MapSession? = null
    private var currentKey: String? = null

    /** The world whose history is open, kept across dimension changes. */
    private var worldId: String? = null
    private var world: WorldHistory? = null

    /** The last world's history closing in the background; a reopen waits for it. */
    private var closing: CompletableFuture<Unit>? = null

    val session: MapSession?
        get() = current

    fun register(platform: MapPlatform) {
        check(!this::platform.isInitialized) { "Map sessions are already registered" }
        this.platform = platform
        ClientEvents.tickEnd.subscribe { tick() }
        ClientEvents.disconnected.subscribe {
            closeCurrent()
            closeWorld()
        }
    }

    fun describeBlocksBelow(): String = platform.describeBlocksBelow()

    fun heldItemId(): ItemId? = platform.heldItemId()

    fun waypointCamera(): WaypointCamera? = platform.waypointCamera()

    private fun tick() {
        val location = platform.location()
        if (location == null) {
            closeCurrent()
            closeWorld()
            return
        }
        val key = location.key
        if (key != currentKey) {
            closeCurrent()
            open(location, key)
        }
        current?.let { session ->
            try {
                session.tick()
            } catch (failure: Exception) {
                logger.error("Map session failed; closing it", failure)
                closeCurrent()
            }
        }
    }

    private fun open(location: MapLocation, key: String) {
        val directory =
            Platform.gameDirectory
                .toPath()
                .resolve("palimpsest")
                .resolve("maps")
                .resolve(location.worldId)
                .resolve(location.dimension)
        // A failed open still takes the key, so it is not retried every tick.
        currentKey = key
        try {
            val history = worldHistory(location.worldId)
            current =
                MapSession(
                    directory,
                    location.ceiling,
                    platform.biomeTints(),
                    platform::scanner,
                    { blocks ->
                        history?.let {
                            DimensionHistory(
                                it,
                                DimensionId(location.dimension.lowercase()),
                                blocks,
                                platform::classify,
                                PalimpsestConfig::commitInterval,
                            )
                        }
                    },
                    platform::worldTime,
                    platform::prospectingMarks,
                    platform.prospectingAvailable(),
                    platform.nodeTrackingAvailable(),
                    platform::claimMarks,
                    platform::requestClaims,
                    platform.claimsAvailable(),
                )
            logger.info("Map session opened for {}", location.key)
        } catch (failure: Exception) {
            logger.error("Could not open map session for {}", location.key, failure)
        }
    }

    /** The world's history, opened on first use and kept while the client stays in that world. */
    private fun worldHistory(id: String): WorldHistory? {
        if (worldId == id) return world
        closeWorld()
        worldId = id
        // The previous world's files must be released before this one locks them.
        closing?.let { runCatching { it.get(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS) } }
        closing = null
        val root = Platform.gameDirectory.toPath().resolve("palimpsest")
        world =
            try {
                WorldHistory.open(
                    root.resolve("maps").resolve(id).resolve("history"),
                    root.resolve("cache").resolve(id),
                    platform::classify,
                )
            } catch (failure: Exception) {
                logger.error("Could not open the history of {}", id, failure)
                null
            }
        return world
    }

    private fun closeWorld() {
        val open = world
        world = null
        worldId = null
        if (open != null) closing = open.closeAsync()
    }

    private const val CLOSE_WAIT_SECONDS = 60L

    private fun closeCurrent() {
        currentKey = null
        val session = current ?: return
        current = null
        try {
            session.close()
        } catch (failure: Exception) {
            logger.error("Closing map session failed", failure)
        }
    }
}
