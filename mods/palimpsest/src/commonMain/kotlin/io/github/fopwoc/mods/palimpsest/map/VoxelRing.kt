package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.render.iso.VoxelMesher
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.palimpsest.db.ChunkPos
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.floor

/**
 * The chunks around the middle of a view as real blocks, read whole from [history]: overhangs,
 * bridges, canopies and, cut at a slice, the rooms under it. [request] each frame tops up reads
 * nearest first, a few at a time, so the ring fills in from the middle while the view keeps
 * drawing; a chunk whose look changes on the [live] map is read again, its old mesh shown until the
 * new one lands. [covered] names the chunks drawn here, which the columns leave out.
 */
class VoxelRing(
    private val history: DimensionHistory,
    private val live: LiveTiles,
    private val shader: TerrainShader,
) : AutoCloseable {
    private val logger = logger<VoxelRing>()

    private data class Moment(val tick: Long, val slice: Int?)

    private class Entry(val mesh: GpuSceneMesh?, val moment: Moment)

    private val lock = Any()
    private val entries = HashMap<ChunkPos, Entry>()
    private val loading = HashSet<ChunkPos>()
    private val stale = HashSet<ChunkPos>()
    private var moment: Moment? = null
    private var center: ChunkPos? = null
    private var dirty = false
    private val onChange: (Collection<TileKey>) -> Unit = { keys ->
        synchronized(lock) {
            for (key in keys) {
                val pos = ChunkPos(key.x, key.z)
                if (pos in entries) stale += pos
            }
        }
    }

    @Volatile
    var meshes: List<GpuSceneMesh> = emptyList()
        private set

    /** Chunk positions drawn as blocks now, packed by [chunkKey]. */
    @Volatile
    var covered: Set<Long> = emptySet()
        private set

    init {
        live.addChangeListener(onChange)
    }

    /**
     * The ring around block ([x], [z]) at [tick] ([Long.MAX_VALUE] for live), cut at [slice]; an
     * inactive ring lets go of everything.
     */
    fun request(x: Double, z: Double, tick: Long, slice: Int?, active: Boolean) {
        synchronized(lock) {
            if (!active) {
                if (entries.isEmpty() && loading.isEmpty()) return
                entries.clear()
                stale.clear()
                center = null
                dirty = true
            } else {
                val wanted = Moment(tick, slice)
                if (wanted != moment) {
                    // A new moment or cut repaints everything; the columns show meanwhile.
                    moment = wanted
                    entries.clear()
                    stale.clear()
                    dirty = true
                }
                val here = ChunkPos(floor(x / CHUNK).toInt(), floor(z / CHUNK).toInt())
                if (here != center) {
                    center = here
                    if (entries.keys.removeIf { distance(it, here) > RADIUS + KEEP_MARGIN })
                        dirty = true
                }
                topUp(here, wanted)
            }
            if (dirty) publish()
        }
    }

    /** Starts reads for the nearest chunks not read yet, or read before their last change. */
    private fun topUp(center: ChunkPos, moment: Moment) {
        if (loading.size >= MAX_IN_FLIGHT) return
        val candidates =
            ORDER.asSequence()
                .map { (dx, dz) -> ChunkPos(center.x + dx, center.z + dz) }
                .filter { it !in loading && (it !in entries || it in stale) }
        for (pos in candidates) {
            if (loading.size >= MAX_IN_FLIGHT) break
            stale -= pos
            loading += pos
            read(pos, moment)
        }
    }

    private fun read(pos: ChunkPos, moment: Moment) {
        history
            .voxels(pos, moment.tick)
            .thenApplyAsync(
                { chunk ->
                    chunk?.let { VoxelMesher.mesh(it, history.palette(shader), moment.slice) }
                },
                mesher,
            )
            .whenComplete { mesh, failure ->
                synchronized(lock) {
                    loading -= pos
                    if (failure != null) {
                        if (
                            failure !is CancellationException &&
                                failure.cause !is CancellationException
                        )
                            logger.error("Could not read the blocks of chunk {}", pos, failure)
                        return@whenComplete
                    }
                    if (
                        moment != this.moment || center?.let { distance(pos, it) > RADIUS } != false
                    )
                        return@whenComplete
                    entries[pos] = Entry(mesh, moment)
                    dirty = true
                }
            }
    }

    private fun publish() {
        dirty = false
        meshes = entries.values.mapNotNull { it.mesh }
        covered = entries.entries.filter { it.value.mesh != null }.mapTo(HashSet()) { key(it.key) }
    }

    override fun close() {
        live.removeChangeListener(onChange)
        synchronized(lock) {
            entries.clear()
            stale.clear()
        }
    }

    companion object {
        /** Chunks each side of the middle one: a 33 × 33 ring. */
        const val RADIUS = 16
        /** Chunks past the ring kept for a while, so panning back and forth reads nothing. */
        private const val KEEP_MARGIN = 4
        private const val MAX_IN_FLIGHT = 8
        private const val CHUNK = 16.0

        /** A chunk position packed into one long, as [covered] holds them. */
        fun chunkKey(x: Int, z: Int): Long =
            (x.toLong() shl Int.SIZE_BITS) or (z.toLong() and 0xFFFFFFFFL)

        private fun key(pos: ChunkPos): Long = chunkKey(pos.x, pos.z)

        private fun distance(a: ChunkPos, b: ChunkPos) = maxOf(abs(a.x - b.x), abs(a.z - b.z))

        /** Offsets in the ring, nearest first. */
        private val ORDER: List<Pair<Int, Int>> =
            (-RADIUS..RADIUS)
                .flatMap { dz -> (-RADIUS..RADIUS).map { dx -> dx to dz } }
                .sortedBy { (dx, dz) -> dx * dx + dz * dz }

        /** Two threads for every view; meshing a chunk takes about a millisecond. */
        private val mesher: ExecutorService =
            Executors.newFixedThreadPool(2) {
                Thread(it, "palimpsest-voxels").apply { isDaemon = true }
            }
    }
}
