package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path
import java.time.Duration
import org.apache.logging.log4j.LogManager

/**
 * The whole map storage behind one door. The mod feeds it chunk views with [observe], calls [tick]
 * once a second, draws through [view], and closes it with the world.
 *
 * Underneath: an [ObservationBroker] coalesces observations, a region-paged history keeps them
 * forever in git-syncable segments, and [MapView] turns them into pages for the screen.
 */
class WorldMap(
    directory: Path,
    palette: IntArray,
    commitInterval: Duration = Duration.ofMinutes(1),
    private val maintenanceEvery: Duration = Duration.ofSeconds(30),
    private val compactEvery: Duration = Duration.ofMinutes(10),
    clock: () -> Long = System::currentTimeMillis,
    onChanged: () -> Unit = {},
) : AutoCloseable {
    private val logger = LogManager.getLogger(WorldMap::class.java)
    private val clock = clock
    val store = MapPageStore(directory, palette, commitInterval = commitInterval, clock = clock)
    val view = MapView(store, onChanged = onChanged)
    private var lastMaintenance = clock()
    private var lastCompaction = clock()

    init {
        logger.info("World map at {}", directory.toAbsolutePath())
    }

    /** The current 16×16 palette view of a chunk; as often as the mod likes. */
    fun observe(chunkX: Int, chunkZ: Int, colors: ByteArray) =
        store.observe(TileKey(chunkX, chunkZ), colors)

    /** Once a second: commits due observations; every [maintenanceEvery] seals, rarely compacts. */
    fun tick() {
        store.commitDue()
        val now = clock()
        if (now - lastMaintenance >= maintenanceEvery.toMillis()) {
            lastMaintenance = now
            val compact = now - lastCompaction >= compactEvery.toMillis()
            if (compact) lastCompaction = now
            val work = if (compact) store.maintain() else store.sealDue()
            if (work > 0) logger.debug("Maintenance: {} regions sealed or compacted", work)
        }
    }

    /** Full flush: commits everything pending and seals; for world save events. */
    fun flush() {
        store.commitAll()
        store.flush()
    }

    override fun close() {
        view.close()
        store.close()
        logger.info("World map closed")
    }

    companion object {
        const val TILE_PIXELS = TileLayer.PIXELS
    }
}
