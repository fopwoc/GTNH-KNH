package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.floor

/**
 * Recent volatile top-down slices, with only the player's current height exposed as [tiles]. A new
 * height shows only once every tile in view has been visited, and the changes of a scan batch reach
 * listeners together at [publish].
 */
class MinimapBroker(
    blocks: BlockTable,
    grassTint: (Int) -> Int,
    foliageTint: (Int) -> Int,
    waterTint: (Int) -> Int,
) : LiveTiles {
    private val lock = Any()
    override val shader =
        TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)
    private val slices = LinkedHashMap<Int, Slice>(4, 0.75f, true)
    private var active: Slice? = null
    private var displayed: Slice? = null
    private var wantedRange: List<Int>? = null
    private var wanted = emptySet<TileKey>()
    private var required = emptySet<TileKey>()
    private val dirty = LinkedHashSet<TileKey>()
    private val listeners = CopyOnWriteArrayList<(Collection<TileKey>) -> Unit>()
    private var ceiling: Int? = null

    /**
     * The ground in view, with a tile halo for relief shading; unloaded distant tiles do not delay
     * a switch.
     */
    fun request(area: GroundArea) {
        val centerX = floor(area.centerX / CHUNK).toInt()
        val centerZ = floor(area.centerZ / CHUNK).toInt()
        val left = maxOf(floor(area.minX / CHUNK).toInt() - 1, centerX - MAX_REACH)
        val right = minOf(floor(area.maxX / CHUNK).toInt() + 1, centerX + MAX_REACH)
        val top = maxOf(floor(area.minZ / CHUNK).toInt() - 1, centerZ - MAX_REACH)
        val bottom = minOf(floor(area.maxZ / CHUNK).toInt() + 1, centerZ + MAX_REACH)
        synchronized(lock) {
            val range = listOf(left, right, top, bottom)
            if (range == wantedRange) return
            wantedRange = range
            wanted = buildSet {
                for (z in top..bottom) for (x in left..right) add(TileKey(x, z))
            }
        }
    }

    fun prepare(centerX: Int, centerZ: Int, radius: Int): List<TileKey> =
        synchronized(lock) {
            required =
                wanted
                    .filterTo(LinkedHashSet()) {
                        abs(it.x - centerX) <= radius && abs(it.z - centerZ) <= radius
                    }
                    .ifEmpty { setOf(TileKey(centerX, centerZ)) }
            required
                .filter { it !in active?.surveyed.orEmpty() }
                .sortedBy { maxOf(abs(it.x - centerX), abs(it.z - centerZ)) }
        }

    /** Called after a scan batch, including a failed lookup of an unloaded chunk. */
    fun surveyed(height: Int, key: TileKey) =
        synchronized(lock) {
            if (height == ceiling) active?.survey(key)
            Unit
        }

    /** Publish one coherent layer only after every visible loaded-area tile has been visited. */
    fun publish() {
        val changed =
            synchronized(lock) {
                val next = active ?: return
                val previous = displayed
                if (previous !== next && !next.surveyed.containsAll(required)) return
                if (previous !== next) {
                    displayed = next
                    dirty.addAll(previous?.tiles?.keys.orEmpty())
                    dirty.addAll(next.tiles.keys)
                }
                dirty.toList().also { dirty.clear() }
            }
        if (changed.isEmpty()) return
        for (listener in listeners) listener(changed)
    }

    private class Slice {
        val surveyed = LinkedHashSet<TileKey>()
        val tiles = LinkedHashMap<TileKey, TileRecord>(256, 0.75f, true)

        fun survey(key: TileKey) {
            surveyed.remove(key)
            surveyed.add(key)
            if (surveyed.size > MAX_TILES) surveyed.remove(surveyed.first())
        }
    }

    /** Selects a cached slice or starts a new one; returns whether it already contains tiles. */
    fun atHeight(height: Int): Boolean {
        return synchronized(lock) {
            if (ceiling == height) return active?.tiles?.isNotEmpty() == true
            val selected = slices.getOrPut(height) { Slice() }
            active = selected
            ceiling = height
            if (displayed == null) displayed = selected
            if (slices.size > MAX_HEIGHTS) {
                slices.entries
                    .firstOrNull { it.value !== active && it.value !== displayed }
                    ?.let { slices.remove(it.key) }
            }
            selected.tiles.isNotEmpty()
        }
    }

    /** Reuses facts only when the chunk proves that the interval between ceilings is empty. */
    fun reuseVisible(height: Int, key: TileKey, unchangedBetween: (Int, Int) -> Boolean): Boolean {
        val donors =
            synchronized(lock) {
                if (height != ceiling || active?.tiles?.containsKey(key) == true) return false
                slices.entries
                    .mapNotNull { (otherHeight, slice) ->
                        if (slice === active) null else slice.tiles[key]?.let { otherHeight to it }
                    }
                    .sortedBy { abs(it.first - height) }
            }
        val donor =
            donors.firstOrNull {
                unchangedBetween(minOf(it.first, height) + 1, maxOf(it.first, height))
            } ?: return false
        observe(height, key, donor.second)
        return true
    }

    fun observe(height: Int, key: TileKey, record: TileRecord) =
        synchronized(lock) {
            val selected = active
            if (height == ceiling) selected?.survey(key)
            if (
                height != ceiling ||
                    selected == null ||
                    selected.tiles[key]?.sameFacts(record) == true
            )
                return@synchronized
            selected.tiles[key] = record
            if (selected === displayed) dirty += key
            if (selected.tiles.size > MAX_TILES) {
                val evicted = selected.tiles.keys.first().also(selected.tiles::remove)
                if (selected === displayed) dirty += evicted
            }
        }

    override fun tiles(x0: Int, z0: Int, width: Int, height: Int): Array<TileRecord?> =
        synchronized(lock) {
            val shown = displayed?.tiles
            Array(width * height) { at ->
                shown?.get(TileKey(x0 + at % width, z0 + at / width))
            }
        }

    override fun addChangeListener(listener: (Collection<TileKey>) -> Unit) {
        listeners += listener
    }

    override fun removeChangeListener(listener: (Collection<TileKey>) -> Unit) {
        listeners -= listener
    }

    private companion object {
        const val CHUNK = 16
        /** Tiles each side of the centre the scanner is asked for at most. */
        const val MAX_REACH = 33
        const val MAX_HEIGHTS = 3
        const val MAX_TILES = 4096
    }
}
