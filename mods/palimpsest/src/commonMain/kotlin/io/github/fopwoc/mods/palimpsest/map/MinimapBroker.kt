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
import kotlin.math.abs
import kotlin.math.floor

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
    private var displayed: Slice? = null
    private var generation = 0L
    private var wantedRange: List<Int>? = null
    private var wanted = emptySet<TileKey>()
    private var required = emptySet<TileKey>()
    private val dirty = LinkedHashSet<TileKey>()

    override val revision: Long
        get() = synchronized(lock) { generation }

    /**
     * Includes a tile halo for relief shading and rotation; unloaded distant tiles do not delay a
     * switch.
     */
    fun request(camera: MapCamera) {
        val halfX = camera.width / (2.0 * camera.pixelsPerBlock)
        val halfZ = camera.height / (2.0 * camera.pixelsPerBlock)
        val centerX = floor(camera.centerX / 16).toInt()
        val centerZ = floor(camera.centerZ / 16).toInt()
        val left = maxOf(floor((camera.centerX - halfX) / 16).toInt() - 1, centerX - 33)
        val right = minOf(floor((camera.centerX + halfX) / 16).toInt() + 1, centerX + 33)
        val top = maxOf(floor((camera.centerZ - halfZ) / 16).toInt() - 1, centerZ - 33)
        val bottom = minOf(floor((camera.centerZ + halfZ) / 16).toInt() + 1, centerZ + 33)
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
                    generation++
                    dirty.addAll(previous?.tiles?.keys.orEmpty())
                    dirty.addAll(next.tiles.keys)
                }
                dirty.toList().also { dirty.clear() }
            }
        invalidate(changed)
    }

    private val listeners = CopyOnWriteArrayList<(Collection<MapPageKey>) -> Unit>()
    private var ceiling: Int? = null

    private inner class Slice {
        val surveyed = LinkedHashSet<TileKey>()

        fun survey(key: TileKey) {
            surveyed.remove(key)
            surveyed.add(key)
            if (surveyed.size > MAX_TILES) surveyed.remove(surveyed.first())
        }

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

    fun observe(height: Int, key: TileKey, record: TileRecord) {
        val (changed, pages) =
            synchronized(lock) {
                val selected = active
                if (height == ceiling) selected?.survey(key)
                if (
                    height != ceiling ||
                        selected == null ||
                        selected.tiles[key]?.sameFacts(record) == true
                )
                    emptyList<TileKey>() to null
                else {
                    selected.tiles[key] = record
                    if (selected === displayed) dirty += key
                    val evicted =
                        if (selected.tiles.size > MAX_TILES)
                            selected.tiles.keys.first().also(selected.tiles::remove)
                        else null
                    if (selected === displayed && evicted != null) dirty += evicted
                    listOfNotNull(key, evicted) to selected.pages
                }
            }
        if (changed.isEmpty()) return
        pages?.invalidateTiles(changed, Long.MAX_VALUE)
    }

    override fun latest(key: MapPageKey, checkActive: () -> Unit): MapPageRaster? =
        synchronized(lock) { displayed }?.pages?.latest(key, checkActive)

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
