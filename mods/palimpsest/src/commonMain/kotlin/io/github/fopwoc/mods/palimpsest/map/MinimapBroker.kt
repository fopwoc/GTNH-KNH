package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.PageBuilder
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.Sample
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Recent volatile top-down slices, with only the player's current height exposed to the view. */
class MinimapBroker(
    blocks: BlockTable,
    grassTint: (Int) -> Int,
    foliageTint: (Int) -> Int,
    waterTint: (Int) -> Int,
) : MapPageSource {
    private val lock = Any()
    private val shader =
        TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)
    private val slices = LinkedHashMap<Int, Slice>(4, 0.75f, true)
    private var active: Slice? = null
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private var ceiling: Int? = null

    private inner class Slice {
        val tiles = LinkedHashMap<TileKey, TileRecord>(256, 0.75f, true)
        private val source =
            object : TileSource {
                override val latestEpoch = 0L

                override fun tile(key: TileKey, epoch: Long): TileRecord? =
                    synchronized(lock) { tiles[key] }

                override fun samples(level: Int, x0: Int, z0: Int, side: Int, epoch: Long) =
                    LongArray(side * side) { Sample.NONE.packed }

                override fun representativeTile(level: Int, x: Int, z: Int): TileKey? = null

                override fun write(epoch: Long, changes: Map<TileKey, TileRecord>): Int =
                    error("Minimap tiles are observations only")

                override fun sealIfDue() = false

                override fun seal() = Unit

                override fun close() = Unit
            }
        val pages =
            MapPageCache(
                PageBuilder(source, shader, pending = { synchronized(lock) { tiles.toMap() } }),
                null,
                MAX_PAGES_PER_SLICE,
            )
    }

    /** Selects a cached slice or starts a new one; returns whether it already contains tiles. */
    fun atHeight(height: Int): Boolean {
        val (changed, cached) =
            synchronized(lock) {
                if (ceiling == height) return active?.tiles?.isNotEmpty() == true
                val old = active?.tiles?.keys.orEmpty()
                val known = slices[height]
                val selected = known ?: Slice().also { slices[height] = it }
                active = selected
                ceiling = height
                if (slices.size > MAX_HEIGHTS) slices.remove(slices.keys.first())
                (old + selected.tiles.keys).distinct() to (known?.tiles?.isNotEmpty() == true)
            }
        invalidate(changed)
        return cached
    }

    /** Borrows a tile for the first pass when both views are above every filled chunk section. */
    fun reuseVisible(height: Int, key: TileKey, highestFilledY: () -> Int): Boolean {
        val donor =
            synchronized(lock) {
                if (height != ceiling || active?.tiles?.containsKey(key) == true) return false
                slices.entries
                    .mapNotNull { (otherHeight, slice) ->
                        if (slice === active) null else slice.tiles[key]?.let { otherHeight to it }
                    }
                    .maxByOrNull { it.first }
            } ?: return false
        val filledY = highestFilledY()
        if (height < filledY || donor.first < filledY) return false
        observe(height, key, donor.second)
        return true
    }

    fun observe(height: Int, key: TileKey, record: TileRecord) {
        val (changed, pages) =
            synchronized(lock) {
                val selected = active
                if (
                    height != ceiling ||
                        selected == null ||
                        selected.tiles[key]?.sameFacts(record) == true
                )
                    emptyList<TileKey>() to null
                else {
                    selected.tiles[key] = record
                    val evicted =
                        if (selected.tiles.size > MAX_TILES)
                            selected.tiles.keys.first().also(selected.tiles::remove)
                        else null
                    listOfNotNull(key, evicted) to selected.pages
                }
            }
        if (changed.isEmpty()) return
        pages?.invalidateTiles(changed, Long.MAX_VALUE)
        invalidate(changed)
    }

    override fun latest(key: MapPageKey, checkActive: () -> Unit): MapPageRaster? =
        synchronized(lock) { active }?.pages?.latest(key, checkActive)

    override fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit): MapPageRaster? =
        null

    override fun addInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners += listener
    }

    override fun removeInvalidationListener(listener: (Collection<MapPageKey>) -> Unit) {
        listeners -= listener
    }

    private fun invalidate(keys: Collection<TileKey>) {
        if (keys.isEmpty()) return
        val affected = keys.flatMapTo(LinkedHashSet()) { MapPageKey.containing(it) }
        for (listener in listeners) listener(affected)
    }

    private companion object {
        const val MAX_HEIGHTS = 3
        const val MAX_TILES = 4096
        const val MAX_PAGES_PER_SLICE = 42
    }
}
