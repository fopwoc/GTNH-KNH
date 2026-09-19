package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import io.github.fopwoc.mods.palimpsest.tree.SegmentSet
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The map's whole storage surface: publish what you see with [observe], draw with [latest] and
 * [historical], call [commitDue] from a slow tick and [close] on unload.
 *
 * Observations pass through an [ObservationBroker], so the latest view renders immediately while
 * the [MapTree] gets one commit per interval. Pages are built by a [PageBuilder] over the tree and
 * cached in a [MapPageCache]; the live builder overlays the broker's uncommitted tiles.
 */
class MapPageStore(
    directory: Path,
    val blocks: BlockTable,
    grassTint: (Int) -> Int = { WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { WHITE },
    sealBytes: Int = SegmentSet.DEFAULT_SEAL_BYTES,
    commitInterval: Duration = Duration.ofMinutes(1),
    clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    val tree = MapTree(directory, blocks.machineId, sealBytes, translateBlock = blocks::translate)
    private val broker = ObservationBroker(::commit, commitInterval, clock)
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private val shader = TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)
    private val builder =
        PageBuilder(
            tree,
            shader,
            tileAt = { key, epoch ->
                (if (epoch == Long.MAX_VALUE) broker.latest(key) else null) ?: tree.tile(key, epoch)
            },
            pending = broker::pending,
        )
    private val pages = MapPageCache(builder, tree)

    init {
        broker.startAfter(tree.latestEpoch)
    }

    /** Publishes the current look of a tile; the live map reflects it. */
    fun observe(key: TileKey, view: TileRecord): Boolean {
        if (!broker.observe(key, view)) return false
        pages.invalidateTiles(listOf(key), Long.MAX_VALUE)
        notifyInvalidated(MapPageKey.containing(key))
        return true
    }

    private fun commit(commit: ObservationBroker.Commit) {
        val result = tree.commit(commit.epoch, commit.tiles)
        if (result.tilesWritten == 0) return
        pages.invalidateTiles(commit.tiles.keys, commit.epoch)
        notifyInvalidated(
            commit.tiles.keys.flatMapTo(LinkedHashSet()) { MapPageKey.containing(it) }
        )
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

    /** Seals the active segment when it is large enough; cheap when it is not. */
    fun sealDue(): Boolean = tree.sealIfDue()

    /** Seals the active segment; for world unload. */
    fun seal() = tree.seal()

    /** Tiles observed this session, committed or not. */
    fun tilesSeen(): Int = broker.seenCount()

    fun cachedLatestPages(): Int = pages.cachedLatestPages()

    fun cachedHistoricalPages(): Int = pages.cachedHistoricalPages()

    override fun close() {
        broker.commitAll()
        tree.close()
        blocks.saveIfDirty()
        pages.clear()
    }

    companion object {
        const val WHITE = 0xFFFFFF
    }
}
