package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path
import java.time.Duration

/**
 * The map's whole storage surface: publish what you see with [observe], draw with [latest] and
 * [historical], call [maintain] from a slow tick and [close] on unload.
 *
 * Observations pass through an [ObservationBroker], so the latest view renders immediately while
 * history is committed at most once per [commitInterval] per tile. Region-paged history and the
 * derived page cache are invalidated together on every write.
 */
class MapPageStore(
    directory: Path,
    palette: IntArray,
    maxOpenRegions: Int = RegionTileHistoryStore.DEFAULT_OPEN_REGIONS,
    commitInterval: Duration = Duration.ofMinutes(1),
    clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val history = RegionTileHistoryStore(directory, maxOpenRegions)
    private val broker = ObservationBroker(::append, commitInterval, clock)
    private val listeners =
        java.util.concurrent.CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private val pages =
        MapPageCache(
            { key, epoch -> tileColors(key, epoch) },
            palette,
            hasChanged = { key, from, to -> history.hasChanges(key, from, to) },
            readSamples = { key, epoch, positions ->
                if (epoch == Long.MAX_VALUE)
                    broker.latest(key)?.let { staged ->
                        ByteArray(positions.size) { staged[positions[it]] }
                    } ?: history.readSamples(key, epoch, positions)?.colors
                else history.readSamples(key, epoch, positions)?.colors
            },
        )

    private fun tileColors(key: TileKey, epoch: Long): ByteArray? =
        (if (epoch == Long.MAX_VALUE) broker.latest(key) else null)
            ?: history.read(key, epoch)?.colors

    /** Publishes the current look of a tile; the live map reflects it on the next page build. */
    fun observe(key: TileKey, colors: ByteArray): Boolean {
        if (!broker.observe(key, colors)) return false
        pages.invalidateTiles(listOf(key), Long.MAX_VALUE)
        notifyInvalidated(MapPageKey.containing(key))
        return true
    }

    /** Direct, uncoalesced write for tools and tests; the broker is the normal path. */
    fun append(layers: List<TileLayer>): TileHistoryStore.AppendResult {
        val result = history.append(layers)
        if (result.layersWritten > 0) {
            pages.invalidate(layers)
            notifyInvalidated(layers.flatMapTo(LinkedHashSet()) { MapPageKey.containing(it.key) })
        }
        return result
    }

    /** Called with every latest-view page whose content may have changed after a write. */
    fun addInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners += listener
    }

    fun removeInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners -= listener
    }

    private fun notifyInvalidated(pages: Collection<MapPageKey>) {
        for (listener in listeners) listener(pages)
    }

    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? =
        pages.latest(key, checkActive)

    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? =
        pages.historical(key, epoch, checkActive)

    /** Commits due observations; call every second or so. */
    fun commitDue(): Int = broker.commitDue()

    /** Commits every pending observation regardless of interval. */
    fun commitAll(): Int = broker.commitAll()

    /** Seals regions whose log is due; cheap when none is. */
    fun sealDue(): Int = history.sealDue()

    /** Seals pending layers and writes dirty index sidecars; cheap when nothing was appended. */
    fun flush() = history.flush()

    /** Commits due observations, seals due logs and merges small segments; for a slow tick. */
    fun maintain(): Int {
        broker.commitDue()
        return history.sealDue() + history.compact()
    }

    override fun close() {
        broker.commitAll()
        history.close()
        pages.clear()
    }
}
