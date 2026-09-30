package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.CurrentTileSource
import io.github.fopwoc.mods.palimpsest.tree.LatestTileStore
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.SegmentSet
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The map's whole storage surface: publish what you see with [observe], draw with [latest] and
 * [historical], call [commitDue] from a slow tick and [close] on unload.
 *
 * Observations pass through an [ObservationBroker], so the latest view renders immediately. With
 * history enabled, accepted changes become tree roots; while paused, they replace current-layer
 * tiles over the last historical root. Pages are built and cached for both modes.
 */
@Suppress("TooGenericExceptionCaught") // Construction failures must release every acquired lock.
class MapPageStore(
    directory: Path,
    val blocks: BlockTable,
    grassTint: (Int) -> Int = { WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { WHITE },
    sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES,
    commitInterval: () -> Duration = { Duration.ofMinutes(1) },
    clock: () -> Long = System::currentTimeMillis,
    val historyEnabled: Boolean = true,
) : MapPageSource, AutoCloseable {
    val tree = MapTree(directory, blocks.machineId, sealBytes, translateBlock = blocks::translate)
    private val current =
        try {
            LatestTileStore(directory.resolve("current"), blocks.machineId, blocks::translate)
        } catch (failure: Exception) {
            tree.use { throw failure }
        }
    private val source: TileSource = if (historyEnabled) tree else CurrentTileSource(tree, current)
    private val broker = ObservationBroker(::commit, commitInterval, clock)
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private val shader =
        TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)
    private val builder =
        PageBuilder(
            source,
            shader,
            overlay = { key, epoch ->
                if (epoch == Long.MAX_VALUE) broker.latest(key) else null
            },
            pending = broker::pending,
        )
    private val pages = MapPageCache(builder, tree)

    init {
        try {
            if (historyEnabled && current.keys().isNotEmpty()) {
                val epoch = maxOf(clock(), source.latestEpoch + 1, current.latestEpoch + 1)
                val changes =
                    current.keys().associateWith { key ->
                        checkNotNull(current.tile(key, Long.MAX_VALUE)).withEpoch(epoch)
                    }
                blocks.saveIfDirty()
                if (tree.commit(epoch, changes).tilesWritten > 0) tree.seal()
                current.clear()
            }
            broker.startAfter(source.latestEpoch)
        } catch (failure: Exception) {
            current.use { tree.use { throw failure } }
        }
    }

    /** Publishes the current look of a tile; the live map reflects it. */
    fun observe(key: TileKey, view: TileRecord, source: Any = directSource): Boolean {
        if (!broker.observe(key, view, source)) return false
        pages.invalidateTiles(listOf(key), Long.MAX_VALUE)
        notifyInvalidated(MapPageKey.affectedBy(key))
        return true
    }

    private fun commit(commit: ObservationBroker.Commit) {
        val changed =
            if (historyEnabled) commit.tiles
            else
                commit.tiles.filter { (key, record) ->
                    source.tile(key, Long.MAX_VALUE)?.sameFacts(record) != true
                }
        if (changed.isEmpty()) return
        if (
            (if (historyEnabled) tree.write(commit.epoch, changed)
            else current.write(commit.epoch, changed)) == 0
        )
            return
        pages.invalidateTiles(changed.keys, commit.epoch)
        notifyInvalidated(changed.keys.flatMapTo(LinkedHashSet()) { MapPageKey.affectedBy(it) })
    }

    override fun latest(key: MapPageKey, checkActive: () -> Unit): MapPageRaster? =
        pages.latest(key, checkActive)

    fun latestTile(key: TileKey): TileRecord? =
        broker.latest(key) ?: source.tile(key, Long.MAX_VALUE)

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

    /** Commits due observations; call every second or so. */
    fun commitDue(): Int = broker.commitDue()

    /** Commits every pending observation regardless of interval. */
    fun commitAll(): Int = broker.commitAll()

    /** Seals the active segment when it is large enough; cheap when it is not. */
    fun sealDue(): Boolean = tree.sealIfDue()

    /** Seals the active segment; for world unload. */
    fun seal() = tree.seal()

    /** Tiles observed this session, committed or not. */
    fun tilesSeen(): Int = broker.seenCount()

    fun currentLayerStats(): Pair<Int, Long> = current.keys().size to current.diskBytes()

    fun cachedLatestPages(): Int = pages.cachedLatestPages()

    fun cachedHistoricalPages(): Int = pages.cachedHistoricalPages()

    override fun close() {
        try {
            current.use {
                tree.use {
                    blocks.saveIfDirty()
                    broker.commitAll()
                }
            }
        } finally {
            pages.clear()
        }
    }

    companion object {
        const val WHITE = 0xFFFFFF
        private val directSource = Any()
    }
}
