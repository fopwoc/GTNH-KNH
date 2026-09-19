package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.RegionTileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileHistoryStore
import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The map's whole storage surface: publish what you see with [observe], draw with [latest] and
 * [historical], call [maintain] from a slow tick and [close] on unload.
 *
 * A tile is a set of [MapChannel]s — colors, biomes — each stored as one or two byte planes in
 * their own region-paged histories under `<directory>/<plane>/` and committed together under one
 * epoch.
 * Observations pass through an [ObservationBroker], so the latest view renders immediately while
 * history is committed at most once per [commitInterval] per tile. The [PixelShader] decides how
 * the channels of a pixel combine into a color when a page is built.
 */
class MapPageStore(
    directory: Path,
    val channels: List<MapChannel>,
    shader: PixelShader,
    maxOpenRegions: Int = RegionTileHistoryStore.DEFAULT_OPEN_REGIONS,
    commitInterval: Duration = Duration.ofMinutes(1),
    clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    /** One channel of palette bytes; the classic single-plane map. */
    constructor(
        directory: Path,
        palette: IntArray,
        maxOpenRegions: Int = RegionTileHistoryStore.DEFAULT_OPEN_REGIONS,
        commitInterval: Duration = Duration.ofMinutes(1),
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        directory,
        listOf(MapChannel.COLORS),
        PixelShader.palette(palette),
        maxOpenRegions,
        commitInterval,
        clock,
    )

    private val planes = channels.flatMap(MapChannel::planes)
    private val histories = planes.map { plane ->
        RegionTileHistoryStore(directory.resolve(plane), maxOpenRegions)
    }
    private val broker = ObservationBroker(planes.size, ::commit, commitInterval, clock)
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private val pages =
        MapPageCache(
            channels,
            readTile = { key, epoch -> tile(key, epoch) },
            shader = shader,
            hasChanged = { key, from, to -> histories.any { it.hasChanges(key, from, to) } },
            readSamples = { key, epoch, positions -> samples(key, epoch, positions) },
        )

    init {
        require(channels.isNotEmpty() && planes.toSet().size == planes.size)
    }

    private fun tile(key: TileKey, epoch: Long): Array<ByteArray?>? {
        val read =
            Array(planes.size) { plane ->
                (if (epoch == Long.MAX_VALUE) broker.latest(key, plane) else null)
                    ?: histories[plane].read(key, epoch)?.colors
            }
        return read.takeIf { it[0] != null }
    }

    private fun samples(key: TileKey, epoch: Long, positions: IntArray): Array<ByteArray?>? {
        val read =
            Array(planes.size) { plane ->
                (if (epoch == Long.MAX_VALUE) broker.latest(key, plane) else null)?.let { staged ->
                    ByteArray(positions.size) { staged[positions[it]] }
                } ?: histories[plane].readSamples(key, epoch, positions)?.colors
            }
        return read.takeIf { it[0] != null }
    }

    /**
     * Publishes the current look of a tile, one array of values per channel, each value within the
     * channel's width; the live map reflects it.
     */
    fun observe(key: TileKey, vararg values: IntArray): Boolean {
        require(values.size == channels.size)
        val view = arrayOfNulls<ByteArray>(planes.size)
        var plane = 0
        for ((channel, array) in channels.zip(values)) {
            require(array.size == TileLayer.PIXELS)
            require(array.all { it in 0..channel.maxValue }) { "Value out of range for $channel" }
            for (byte in 0 until channel.bytes) {
                val shift = byte * Byte.SIZE_BITS
                view[plane++] = ByteArray(TileLayer.PIXELS) { (array[it] shr shift).toByte() }
            }
        }
        return observePlanes(key, view.requireNoNulls())
    }

    /** [observe] for a map whose channels are all a byte wide, straight from palette bytes. */
    fun observe(key: TileKey, vararg planes: ByteArray): Boolean {
        require(planes.size == channels.size && channels.all { it.bytes == 1 })
        return observePlanes(key, arrayOf(*planes))
    }

    private fun observePlanes(key: TileKey, view: Array<ByteArray>): Boolean {
        if (!broker.observe(key, view)) return false
        pages.invalidateTiles(listOf(key), Long.MAX_VALUE)
        notifyInvalidated(MapPageKey.containing(key))
        return true
    }

    /**
     * Direct, uncoalesced write to one plane for tools and tests; the broker is the normal path.
     */
    fun append(
        layers: List<TileLayer>,
        plane: String = planes[0],
    ): TileHistoryStore.AppendResult {
        val result = histories[indexOf(plane)].append(layers)
        if (result.layersWritten > 0) {
            pages.invalidate(layers)
            notifyInvalidated(layers.flatMapTo(LinkedHashSet()) { MapPageKey.containing(it.key) })
        }
        return result
    }

    private fun commit(commit: ObservationBroker.Commit) {
        val touched = LinkedHashSet<MapPageKey>()
        for ((plane, layers) in commit.layers.withIndex()) {
            if (histories[plane].append(layers).layersWritten > 0) {
                pages.invalidate(layers)
                layers.flatMapTo(touched) { MapPageKey.containing(it.key) }
            }
        }
        if (touched.isNotEmpty()) notifyInvalidated(touched)
    }

    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? =
        pages.latest(key, checkActive)

    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? =
        pages.historical(key, epoch, checkActive)

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

    /** Commits due observations; call every second or so. */
    fun commitDue(): Int = broker.commitDue()

    /** Commits every pending observation regardless of interval. */
    fun commitAll(): Int = broker.commitAll()

    /** Seals regions whose log is due; cheap when none is. */
    fun sealDue(): Int = histories.sumOf(RegionTileHistoryStore::sealDue)

    /** Seals pending layers and writes dirty index sidecars; cheap when nothing was appended. */
    fun flush() = histories.forEach(RegionTileHistoryStore::flush)

    /** Flushes and merges small segments; meant for world unload or an idle tick. */
    fun maintain(): Int {
        flush()
        return histories.sumOf(RegionTileHistoryStore::compact)
    }

    override fun close() {
        broker.commitAll()
        histories.forEach(RegionTileHistoryStore::close)
        pages.clear()
    }

    private fun indexOf(plane: String): Int =
        planes.indexOf(plane).also { require(it >= 0) { "Unknown plane $plane" } }
}
