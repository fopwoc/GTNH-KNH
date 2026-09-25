package io.github.fopwoc.mods.palimpsest.client.map

import io.github.fopwoc.mods.framework.event.ClientEvents
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.platform.Platform

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

    val session: MapSession?
        get() = current

    fun register(platform: MapPlatform) {
        check(!this::platform.isInitialized) { "Map sessions are already registered" }
        this.platform = platform
        ClientEvents.tickEnd.subscribe { tick() }
        ClientEvents.disconnected.subscribe { closeCurrent() }
    }

    fun describeBlocksBelow(): String = platform.describeBlocksBelow()

    private fun tick() {
        val location = platform.location()
        if (location == null) {
            closeCurrent()
            return
        }
        if (location.key != currentKey) {
            closeCurrent()
            open(location)
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

    private fun open(location: MapLocation) {
        val directory = Platform.gameDirectory.toPath().resolve("palimpsest").resolve("maps")
            .resolve(location.worldId).resolve(location.dimension)
        // A failed open still takes the key, so it is not retried every tick.
        currentKey = location.key
        try {
            current = MapSession(directory, location.ceiling, platform.biomeTints(), platform::scanner)
            logger.info("Map session opened for {}", location.key)
        } catch (failure: Exception) {
            logger.error("Could not open map session for {}", location.key, failure)
        }
    }

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
