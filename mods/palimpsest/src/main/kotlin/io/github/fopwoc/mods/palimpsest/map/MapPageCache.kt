package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.storage.TileKey
import io.github.fopwoc.mods.palimpsest.storage.TileLayer
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Builds fixed-size pages from one deterministic source sample per output pixel.
 *
 * Pages are built outside the cache lock so appends and other builds are not blocked by tile reads;
 * a build whose page was invalidated while it ran is returned but not cached.
 */
class MapPageCache(
    private val channels: List<MapChannel>,
    private val readTile: (TileKey, Long) -> Array<ByteArray?>?,
    private val shader: PixelShader,
    private val maxLatestPages: Int = 128,
    private val hasChanged: ((TileKey, Long, Long) -> Boolean)? = null,
    private val readSamples: ((TileKey, Long, IntArray) -> Array<ByteArray?>?)? = null,
) {
    private val planes = channels.sumOf(MapChannel::bytes)

    init {
        require(channels.isNotEmpty())
        require(maxLatestPages > 0)
    }

    /**
     * One channel of palette bytes, the classic case; keeps callers with a plain palette simple.
     */
    constructor(
        readTile: (TileKey, Long) -> ByteArray?,
        palette: IntArray,
        maxLatestPages: Int = 128,
        hasChanged: ((TileKey, Long, Long) -> Boolean)? = null,
        readSamples: ((TileKey, Long, IntArray) -> ByteArray?)? = null,
    ) : this(
        listOf(MapChannel.COLORS),
        { key, epoch -> readTile(key, epoch)?.let { arrayOf<ByteArray?>(it) } },
        PixelShader.palette(palette),
        maxLatestPages,
        hasChanged,
        readSamples?.let { sample ->
            { key, epoch, positions ->
                sample(key, epoch, positions)?.let { arrayOf<ByteArray?>(it) }
            }
        },
    )

    /** Bounded page table that remembers which in-flight builds it invalidated. */
    private inner class Table {
        val pages =
            object : LinkedHashMap<MapPageKey, MapPageRaster?>(maxLatestPages, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<MapPageKey, MapPageRaster?>
                ): Boolean = size > maxLatestPages
            }
        private val building = HashMap<MapPageKey, Int>()
        private val stale = HashSet<MapPageKey>()

        fun remove(key: MapPageKey) {
            pages.remove(key)
            if (key in building) stale += key
        }

        fun clear() {
            pages.clear()
            stale += building.keys
        }

        fun begin(key: MapPageKey) {
            building.merge(key, 1, Int::plus)
        }

        fun end(key: MapPageKey, raster: MapPageRaster?, store: Boolean) {
            val remaining = building.merge(key, -1, Int::plus) ?: 0
            if (remaining <= 0) building.remove(key)
            if (store && key !in stale) pages[key] = raster
            if (remaining <= 0) stale.remove(key)
        }
    }

    private val lock = Any()
    private val latest = Table()
    private val historical = Table()
    private var historicalEpoch: Long? = null
    private val reads = AtomicLong()

    /** Drops latest pages of every appended tile and historical pages only when they can change. */
    fun invalidate(layers: Collection<TileLayer>) =
        synchronized(lock) {
            val pinned = historicalEpoch
            for (layer in layers) {
                removePages(latest, layer.key)
                if (pinned != null && layer.epoch <= pinned) removePages(historical, layer.key)
            }
        }

    /** Same as [invalidate] when only the earliest appended epoch of the batch is known. */
    fun invalidateTiles(tiles: Collection<TileKey>, earliestEpoch: Long) =
        synchronized(lock) {
            val historyAffected = historicalEpoch?.let { earliestEpoch <= it } ?: false
            for (tile in tiles) {
                removePages(latest, tile)
                if (historyAffected) removePages(historical, tile)
            }
        }

    private fun removePages(table: Table, tile: TileKey) {
        for (lod in 0..MapPageKey.MAX_LOD) table.remove(
            MapPageKey.containingTile(tile.x, tile.z, lod)
        )
    }

    fun latest(key: MapPageKey, checkActive: () -> Unit = {}): MapPageRaster? {
        synchronized(lock) {
            if (latest.pages.containsKey(key)) return latest.pages[key]
            latest.begin(key)
        }
        return buildTracked(latest, key, Long.MAX_VALUE, checkActive) { true }
    }

    /** Keeps one historical time and patches only tiles changed between observations. */
    fun historical(key: MapPageKey, epoch: Long, checkActive: () -> Unit = {}): MapPageRaster? {
        require(epoch >= 0)
        checkActive()
        synchronized(lock) {
            val previous = historicalEpoch
            if (previous != epoch) {
                if (previous != null && hasChanged != null) {
                    advanceHistorical(previous, epoch, hasChanged, checkActive)
                } else {
                    historical.clear()
                }
                historicalEpoch = epoch
            }
            if (historical.pages.containsKey(key)) return historical.pages[key]
            historical.begin(key)
        }
        return buildTracked(historical, key, epoch, checkActive) { historicalEpoch == epoch }
    }

    fun cachedLatestPages(): Int = synchronized(lock) { latest.pages.size }

    fun cachedHistoricalPages(): Int = synchronized(lock) { historical.pages.size }

    fun tileReadCount(): Long = reads.get()

    fun clear() =
        synchronized(lock) {
            latest.clear()
            historical.clear()
            historicalEpoch = null
        }

    private inline fun buildTracked(
        table: Table,
        key: MapPageKey,
        epoch: Long,
        noinline checkActive: () -> Unit,
        stillWanted: () -> Boolean,
    ): MapPageRaster? {
        var raster: MapPageRaster? = null
        var built = false
        try {
            raster = build(key, epoch, checkActive)
            built = true
        } finally {
            synchronized(lock) { table.end(key, raster, built && stillWanted()) }
        }
        return raster
    }

    private fun build(key: MapPageKey, epoch: Long, checkActive: () -> Unit): MapPageRaster? {
        val pixels = ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
        val positions = samplePositions(minOf(key.lod, 4))
        var present = false
        forEachCell(key) { tile, x, z, side ->
            checkActive()
            val samples = tile?.let { load(it, epoch, key.lod, positions) }
            if (samples != null) {
                present = true
                writeSamples(pixels, x, z, side, samples)
            }
        }
        checkActive()
        return if (present) MapPageRaster(pixels) else null
    }

    private fun advanceHistorical(
        from: Long,
        to: Long,
        changed: (TileKey, Long, Long) -> Boolean,
        checkActive: () -> Unit,
    ) {
        val updates = HashMap<MapPageKey, MapPageRaster?>()
        for ((key, old) in historical.pages.toList()) {
            val positions = samplePositions(minOf(key.lod, 4))
            var pixels: ByteArray? = null
            forEachCell(key) { tile, x, z, side ->
                checkActive()
                if (tile != null && changed(tile, from, to)) {
                    if (pixels == null)
                        pixels =
                            old?.copyPixels() ?: ByteArray(MapPageKey.SIDE * MapPageKey.SIDE * 4)
                    writeSamples(pixels, x, z, side, load(tile, to, key.lod, positions))
                }
            }
            if (pixels != null) {
                updates[key] =
                    if (pixels.indices.step(4).any { pixels[it + 3].toInt() != 0 })
                        MapPageRaster(pixels)
                    else null
            }
        }
        checkActive()
        historical.pages.putAll(updates)
    }

    private fun load(key: TileKey, epoch: Long, lod: Int, positions: IntArray): Array<ByteArray?>? {
        reads.incrementAndGet()
        if (lod == 0) return readTile(key, epoch)
        return readSamples?.invoke(key, epoch, positions)
            ?: if (readSamples == null) {
                readTile(key, epoch)?.let { tile ->
                    Array(planes) { plane ->
                        tile[plane]?.let { bytes ->
                            ByteArray(positions.size) { bytes[positions[it]] }
                        }
                    }
                }
            } else null
    }

    private fun samplePositions(lod: Int): IntArray {
        val step = 1 shl lod
        val side = TileLayer.SIDE / step
        return IntArray(side * side) { index ->
            val x = (index % side) * step + step / 2
            val z = (index / side) * step + step / 2
            z * TileLayer.SIDE + x
        }
    }

    private fun forEachCell(key: MapPageKey, visit: (TileKey?, Int, Int, Int) -> Unit) {
        if (key.lod <= 4) {
            val side = TileLayer.SIDE shr key.lod
            val tileSide = MapPageKey.SIDE / side
            for (tileZ in 0 until tileSide) for (tileX in 0 until tileSide) {
                val x = key.x.toLong() * tileSide + tileX
                val z = key.z.toLong() * tileSide + tileZ
                visit(tileKey(x, z), tileX * side, tileZ * side, side)
            }
            return
        }
        val tileStride = 1L shl (key.lod - 4)
        val side = (1 shl (key.lod - 4)).coerceAtMost(8)
        for (z in 0 until MapPageKey.SIDE step side) for (x in 0 until MapPageKey.SIDE step side) {
            val tileX = (key.x.toLong() * MapPageKey.SIDE + x) * tileStride
            val tileZ = (key.z.toLong() * MapPageKey.SIDE + z) * tileStride
            visit(tileKey(tileX, tileZ), x, z, side)
        }
    }

    private fun tileKey(x: Long, z: Long): TileKey? =
        if (
            x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() &&
                z in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
        )
            TileKey(x.toInt(), z.toInt())
        else null

    private fun writeSamples(
        pixels: ByteArray,
        x: Int,
        z: Int,
        side: Int,
        samples: Array<ByteArray?>?,
    ) {
        val primary = samples?.get(0)
        require(primary == null || primary.size == 1 || primary.size == side * side)
        val values = IntArray(channels.size)
        for (localZ in 0 until side) for (localX in 0 until side) {
            val target = ((z + localZ) * MapPageKey.SIDE + x + localX) * 4
            if (samples == null || primary == null) {
                pixels.fill(0, target, target + 4)
                continue
            }
            val at = if (primary.size == 1) 0 else localZ * side + localX
            var plane = 0
            for ((channel, width) in channels.withIndex()) {
                var value = 0
                for (byte in 0 until width.bytes) {
                    val bytes = samples[plane++]
                    if (bytes == null) {
                        value = -1
                        continue
                    }
                    val sample = bytes[if (bytes.size == 1) 0 else at].toInt() and 255
                    if (value >= 0) value = value or (sample shl (byte * Byte.SIZE_BITS))
                }
                values[channel] = value
            }
            val color = shader.argb(values)
            if (color ushr 24 == 0) {
                pixels.fill(0, target, target + 4)
            } else {
                pixels[target] = (color ushr 16).toByte()
                pixels[target + 1] = (color ushr 8).toByte()
                pixels[target + 2] = color.toByte()
                pixels[target + 3] = (color ushr 24).toByte()
            }
        }
    }
}
