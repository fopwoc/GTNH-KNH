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
    private val slices = LinkedHashMap<Int, LinkedHashMap<TileKey, TileRecord>>(4, 0.75f, true)
    private var tiles = LinkedHashMap<TileKey, TileRecord>(256, 0.75f, true)
    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private var ceiling: Int? = null
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
    private val pages =
        MapPageCache(
            PageBuilder(
                source,
                TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint),
                pending = { synchronized(lock) { tiles.toMap() } },
            ),
            null,
        )

    /** Selects a cached slice or starts a new one; returns whether it already contains tiles. */
    fun atHeight(height: Int): Boolean {
        val (changed, cached) =
            synchronized(lock) {
                if (ceiling == height) return tiles.isNotEmpty()
                val old = tiles.keys.toList()
                val known = slices[height]
                tiles =
                    known
                        ?: LinkedHashMap<TileKey, TileRecord>(256, 0.75f, true).also {
                            slices[height] = it
                        }
                ceiling = height
                if (slices.size > MAX_HEIGHTS) slices.remove(slices.keys.first())
                (old + tiles.keys).distinct() to (known?.isNotEmpty() == true)
            }
        pages.clear()
        invalidate(changed)
        return cached
    }

    fun observe(height: Int, key: TileKey, record: TileRecord) {
        val changed =
            synchronized(lock) {
                if (height != ceiling || tiles[key]?.sameFacts(record) == true) emptyList()
                else {
                    tiles[key] = record
                    val evicted =
                        if (tiles.size > MAX_TILES) tiles.keys.first().also(tiles::remove) else null
                    listOfNotNull(key, evicted)
                }
            }
        if (changed.isEmpty()) return
        pages.invalidateTiles(changed, Long.MAX_VALUE)
        invalidate(changed)
    }

    override fun latest(key: MapPageKey, checkActive: () -> Unit): MapPageRaster? =
        pages.latest(key, checkActive)

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
    }
}
