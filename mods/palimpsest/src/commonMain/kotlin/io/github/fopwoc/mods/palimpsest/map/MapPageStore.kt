package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The map's pages: publish what you see with [observe], draw with [latest] and [historical].
 *
 * [tiles] is history, read-only here. What the scanner sees passes through an [ObservationBroker]
 * that keeps the newest look of every tile this session, so the live view shows it at once and long
 * before it reaches history; the broker only confirms looks, it writes nothing.
 */
class MapPageStore(
    val blocks: BlockTable,
    private val tiles: TileSource,
    grassTint: (Int) -> Int = { WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { WHITE },
    commitInterval: () -> Duration = { Duration.ofMinutes(1) },
    clock: () -> Long = System::currentTimeMillis,
    sampleBudget: PageSampleBudget = PageSampleBudget(),
) : MapPageSource, AutoCloseable {
    private val broker = ObservationBroker({}, commitInterval, clock)
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private val shader =
        TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)
    private val builder =
        PageBuilder(
            tiles,
            shader,
            overlay = { key, epoch -> if (epoch == Long.MAX_VALUE) broker.latest(key) else null },
            pending = broker::pending,
        )
    private val pages = MapPageCache(builder, tiles, sampleBudget = sampleBudget)

    /** Publishes the current look of a tile; the live map reflects it. */
    fun observe(key: TileKey, view: TileRecord, source: Any = directSource): Boolean {
        if (!broker.observe(key, view, source)) return false
        pages.invalidateTiles(listOf(key), Long.MAX_VALUE)
        notifyInvalidated(MapPageKey.affectedBy(key))
        return true
    }

    override fun latest(key: MapPageKey, checkActive: () -> Unit): MapPageRaster? =
        pages.latest(key, checkActive)

    fun latestTile(key: TileKey): TileRecord? =
        broker.latest(key) ?: tiles.tile(key, Long.MAX_VALUE)

    override fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit): MapPageRaster? =
        pages.historical(key, epoch, checkActive)

    /** Called with every latest-view page whose content may have changed after a write. */
    override fun addInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners += listener
    }

    override fun removeInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners -= listener
    }

    private fun notifyInvalidated(pages: Collection<MapPageKey>) {
        for (listener in listeners) listener(pages)
    }

    /** Confirms due looks; call every second or so. */
    fun commitDue(): Int = broker.commitDue()

    /** Tiles observed this session. */
    fun tilesSeen(): Int = broker.seenCount()

    fun cachedLatestPages(): Int = pages.cachedLatestPages()

    fun cachedHistoricalPages(): Int = pages.cachedHistoricalPages()

    override fun close() = pages.clear()

    companion object {
        const val WHITE = 0xFFFFFF
        private val directSource = Any()
    }
}
