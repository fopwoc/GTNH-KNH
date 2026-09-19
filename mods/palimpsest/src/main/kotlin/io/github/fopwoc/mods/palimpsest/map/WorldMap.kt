package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.SegmentSet
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.logging.log4j.LogManager

/**
 * The whole map storage behind one door. The mod feeds it chunk views with [observe], calls [tick]
 * once a second from the game thread, draws through [view], and closes it with the world.
 *
 * [tick] only commits observations (cheap, no fsync); sealing runs on one background thread so the
 * game thread never waits on segment I/O. Underneath: an [ObservationBroker] coalesces
 * observations, a persistent quadtree keeps them forever in git-syncable segments, and [MapView]
 * turns them into pages for the screen.
 */
class WorldMap(
    val directory: Path,
    blocks: BlockTable,
    grassTint: (Int) -> Int = { MapPageStore.WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { MapPageStore.WHITE },
    sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES,
    commitInterval: () -> Duration = { Duration.ofMinutes(1) },
    private val maintenanceEvery: Duration = Duration.ofSeconds(30),
    private val clock: () -> Long = System::currentTimeMillis,
    onChanged: () -> Unit = {},
) : AutoCloseable {
    private val logger = LogManager.getLogger(WorldMap::class.java)
    val store =
        MapPageStore(
            directory,
            blocks,
            grassTint,
            foliageTint,
            waterTint,
            sealBytes,
            commitInterval,
            clock,
        )
    val view = MapView(store, onChanged = onChanged)

    /**
     * Epoch of the map's first run, kept in a synced `created` file; the time slider's left end.
     */
    val createdEpoch: Long = readOrWriteCreated()

    private val maintenance = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "palimpsest-maintenance").apply { isDaemon = true }
    }
    private val maintaining = AtomicBoolean(false)
    private val committing = AtomicBoolean(false)
    private var lastMaintenance = clock()

    init {
        logger.info("World map at {}", directory.toAbsolutePath())
    }

    /** The current 16×16 view of a chunk; as often as the mod likes. */
    fun observe(chunkX: Int, chunkZ: Int, view: TileRecord) =
        store.observe(TileKey(chunkX, chunkZ), view)

    /**
     * Once a second: commits due observations off-thread; every [maintenanceEvery] seals a full
     * segment.
     */
    @Suppress("TooGenericExceptionCaught") // The maintenance thread must survive any failure.
    fun tick() {
        if (committing.compareAndSet(false, true)) {
            maintenance.execute {
                try {
                    store.commitDue()
                } catch (failure: Exception) {
                    logger.error("Map commit failed", failure)
                } finally {
                    committing.set(false)
                }
            }
        }
        val now = clock()
        if (now - lastMaintenance < maintenanceEvery.toMillis()) return
        lastMaintenance = now
        if (!maintaining.compareAndSet(false, true)) return
        maintenance.execute {
            try {
                if (store.sealDue()) logger.debug("Maintenance: sealed a segment")
            } catch (failure: Exception) {
                logger.error("Map maintenance failed", failure)
            } finally {
                maintaining.set(false)
            }
        }
    }

    /** Full flush on the caller's thread: commits everything pending; for world save. */
    fun flush() {
        store.commitAll()
    }

    override fun close() {
        view.close()
        maintenance.shutdown()
        if (!maintenance.awaitTermination(30, TimeUnit.SECONDS)) {
            logger.warn("Map maintenance did not finish in time; closing anyway")
        }
        store.close()
        logger.info("World map closed")
    }

    private fun readOrWriteCreated(): Long {
        val file = directory.resolve(CREATED_FILE)
        if (Files.isRegularFile(file)) {
            Files.readString(file).trim().toLongOrNull()?.let {
                return it
            }
        }
        val now = clock()
        Files.createDirectories(directory)
        Files.writeString(file, "$now\n")
        return now
    }

    companion object {
        const val CREATED_FILE = "created"
    }
}
