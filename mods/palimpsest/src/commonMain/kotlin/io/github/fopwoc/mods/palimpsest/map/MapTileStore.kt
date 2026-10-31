package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.tree.TileRecord
import io.github.fopwoc.mods.palimpsest.tree.TileSource
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The map's tiles: publish what you see with [observe], read the newest look with [latestTile] and
 * [tiles].
 *
 * [history] is read-only here. What the scanner sees passes through an [ObservationBroker] that
 * keeps the newest look of every tile this session, so the live view shows it at once and long
 * before it reaches history; the broker only confirms looks, it writes nothing.
 */
class MapTileStore(
    val blocks: BlockTable,
    private val history: TileSource,
    grassTint: (Int) -> Int = { WHITE },
    foliageTint: (Int) -> Int = grassTint,
    waterTint: (Int) -> Int = { WHITE },
    commitInterval: () -> Duration = { Duration.ofMinutes(1) },
    clock: () -> Long = System::currentTimeMillis,
) : LiveTiles {
    private val broker = ObservationBroker({}, commitInterval, clock)
    private val listeners = CopyOnWriteArrayList<(Collection<TileKey>) -> Unit>()
    override val shader =
        TerrainShader(blocks::color, blocks::tint, grassTint, foliageTint, waterTint)

    /** Publishes the current look of a tile; the live map reflects it. */
    fun observe(key: TileKey, view: TileRecord, source: Any = directSource): Boolean {
        if (!broker.observe(key, view, source)) return false
        val changed = listOf(key)
        for (listener in listeners) listener(changed)
        return true
    }

    fun latestTile(key: TileKey): TileRecord? =
        broker.latest(key) ?: history.tile(key, Long.MAX_VALUE)

    /**
     * [latestTile] for a rectangle: history read a square at a time, what the scan saw since the
     * last commit laid over it. Blocking.
     */
    override fun tiles(x0: Int, z0: Int, width: Int, height: Int): Array<TileRecord?> {
        val out = arrayOfNulls<TileRecord>(width * height)
        for (squareZ in 0 until height step READ_SIDE) for (squareX in
            0 until width step READ_SIDE) {
            val stored = history.tiles(x0 + squareX, z0 + squareZ, READ_SIDE, Long.MAX_VALUE)
            for (dz in 0 until minOf(READ_SIDE, height - squareZ)) for (dx in
                0 until minOf(READ_SIDE, width - squareX)) {
                val x = squareX + dx
                val z = squareZ + dz
                out[z * width + x] =
                    broker.latest(TileKey(x0 + x, z0 + z)) ?: stored[dz * READ_SIDE + dx]
            }
        }
        return out
    }

    override fun addChangeListener(listener: (Collection<TileKey>) -> Unit) {
        listeners += listener
    }

    override fun removeChangeListener(listener: (Collection<TileKey>) -> Unit) {
        listeners -= listener
    }

    /** Confirms due looks; call every second or so. */
    fun commitDue(): Int = broker.commitDue()

    /** Tiles observed this session. */
    fun tilesSeen(): Int = broker.seenCount()

    companion object {
        /** Tiles a side of one history read for [tiles]. */
        private const val READ_SIDE = 128

        const val WHITE = 0xFFFFFF
        private val directSource = Any()
    }
}
