package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path

/**
 * Region-paged history and its disposable derived LOD cache, with write invalidation together.
 *
 * Page reads are not serialized against each other or against appends; the history store and the
 * page cache each guard their own state.
 */
class MapPageStore(directory: Path, palette: IntArray, maxOpenRegions: Int = 256) : AutoCloseable {
    private val history = RegionTileHistoryStore(directory, maxOpenRegions)
    private val pages =
        MapPageCache(
            { key, epoch -> history.read(key, epoch)?.colors },
            palette,
            hasChanged = { key, from, to -> history.hasChanges(key, from, to) },
            readSamples = { key, epoch, positions ->
                history.readSamples(key, epoch, positions)?.colors
            },
        )

    fun append(layers: List<TileLayer>): TileHistoryStore.AppendResult {
        val result = history.append(layers)
        if (result.layersWritten > 0) pages.invalidate(layers)
        return result
    }

    fun latest(key: MapPageKey): MapPageRaster? = pages.latest(key)

    fun historical(key: MapPageKey, epoch: Long): MapPageRaster? = pages.historical(key, epoch)

    /** Seals pending layers and writes dirty index sidecars; cheap when nothing was appended. */
    fun flush() = history.flush()

    /** Flushes and merges small segments; meant for world unload or an idle tick. */
    fun maintain(): Int {
        history.flush()
        return history.compact()
    }

    @Synchronized
    fun reload() {
        history.close()
        pages.clear()
    }

    @Synchronized
    override fun close() {
        history.close()
        pages.clear()
    }
}
