package io.github.fopwoc.mods.palimpsest.map

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuSceneMesh
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.render.iso.Heightfield
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.mods.palimpsest.render.iso.TerrainMeshes
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.WorldTick
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The ground a map view stands on, read around what the camera sees: near by from the [live] tiles,
 * with what the scan saw since the last commit; with a [history], far out from its overview, at
 * past moments, and with a slice, the world seen from that height down, all as of its last commit.
 * Without one the view is live and reaches only [MAX_LIVE_SIDE] chunks around its centre.
 *
 * The middle of the view gets the detail the zoom asks for; when that would be too many columns for
 * the whole view, an outer layer covers the rest more coarsely, with a hole where the middle lies.
 * Close enough to make out blocks, the middle of a view with history is drawn from whole chunks by
 * a [VoxelRing], and the columns step aside under it.
 *
 * Each layer has one read in flight at a time and keeps its last field drawn until the next lands;
 * fields are meshed on the reader thread, reusing regions that did not change. A live view reads
 * again when a tile in view changed, at most every [liveRefreshNanos].
 */
class IsoTerrain(
    private val history: DimensionHistory?,
    private val live: LiveTiles,
    private val liveRefreshNanos: Long = LIVE_REFRESH_NANOS,
) : AutoCloseable {
    private val logger = logger<IsoTerrain>()
    private val voxels = history?.let { VoxelRing(it, live, live.shader) }
    private val inner = Layer()
    private val outer = Layer()
    @Volatile private var changes = 0L
    @Volatile private var watched: ChunkWindow? = null
    private val onChange: (Collection<TileKey>) -> Unit = { keys ->
        val window = watched
        if (
            window != null &&
                keys.any {
                    it.x - window.x0 in 0 until window.width &&
                        it.z - window.z0 in 0 until window.height
                }
        )
            changes++
    }
    private var seenChanges = 0L
    private var generation = 0L
    private var refreshedAt = 0L

    init {
        live.addChangeListener(onChange)
    }

    /** [tick] is [Long.MAX_VALUE] for live, and [generation] then counts its refreshes. */
    private data class Key(
        val window: ChunkWindow,
        val step: Int,
        val tick: Long,
        val generation: Long,
        val slice: Int?,
    )

    /** A rectangle of whole blocks, [minX] and [minZ] in, [maxX] and [maxZ] out. */
    private data class Hole(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int)

    /** What a layer's columns leave out: chunks drawn as blocks, and the inner layer's ground. */
    private data class Skips(val covered: Set<Long>, val hole: Hole?)

    /** One field of columns, read and meshed on its own. */
    private inner class Layer {
        val meshing = TerrainMeshes()
        var wanted: Key? = null
        var shown: Key? = null
        var cancelPending: (() -> Unit)? = null
        @Volatile var field: Heightfield? = null
        @Volatile var meshes: List<GpuSceneMesh> = emptyList()
        @Volatile var meshedSkips: Skips? = null
        @Volatile var remeshing = false
        var remeshedAt = 0L

        /** The ground this layer's field covers, or null before one landed. */
        val ground: Hole?
            get() =
                this.field?.let {
                    Hole(
                        it.originX,
                        it.originZ,
                        it.originX + it.columns * it.step,
                        it.originZ + it.rows * it.step,
                    )
                }

        /** Lets go of everything, for a view that needs only one layer. */
        fun drop() {
            cancelPending?.invoke()
            cancelPending = null
            wanted = null
            shown = null
            field = null
            meshes = emptyList()
            meshedSkips = null
        }
    }

    /** The top of the ground at a block column, finest layer first; null where none was read. */
    fun topAt(x: Int, z: Int): Int? = inner.field?.topAt(x, z) ?: outer.field?.topAt(x, z)

    /** Both layers' columns and the ring's blocks as world-space meshes for the GPU. */
    val meshes: List<GpuSceneMesh>
        get() = inner.meshes + outer.meshes + voxels?.meshes.orEmpty()

    /**
     * Asks for whatever [camera] needs at [time] cut at [slice]; cheap when it is already there.
     * Without history only the live, uncut world can be shown.
     */
    @Synchronized
    fun request(camera: IsoCamera, time: MapTime, slice: Int?) {
        val slice = slice.takeIf { history != null }
        val tick = (time as? MapTime.At)?.tick?.takeIf { history != null } ?: Long.MAX_VALUE
        val now = System.nanoTime()
        val isLive = tick == Long.MAX_VALUE
        if (isLive && changes != seenChanges && now - refreshedAt >= liveRefreshNanos) {
            seenChanges = changes
            refreshedAt = now
            generation++
        }
        voxels?.request(
            camera.centerX,
            camera.centerZ,
            tick,
            slice,
            camera.pixelsPerBlock >= VOXEL_PIXELS_PER_BLOCK,
        )
        // What the canvas sees of the ground between the slice, or a generous sky, and the floor.
        val area =
            camera.footprint(
                camera.centerY - VIEW_BELOW,
                (slice ?: (camera.centerY + VIEW_ABOVE).toInt()).toDouble(),
            )
        var fine = 1
        while (fine < MIN_CELL_PIXELS / camera.pixelsPerBlock) fine *= 2
        // Slices are decoded chunk by chunk and a view without history reads every tile live:
        // either way a column per chunk is the coarsest there is, so one layer has to do.
        val bounded = slice != null || history == null
        var coarse = fine
        while (cells(area, coarse) > MAX_CELLS) coarse *= 2
        if (coarse == fine || bounded) {
            val step = if (bounded) coarse.coerceAtMost(CHUNK) else coarse
            request(inner, camera, area, step, tick, slice, align = null)
            outer.drop()
        } else {
            // The middle at full detail, the size of half the budget, on the outer layer's grid so
            // its hole cuts whole cells.
            val half = sqrt(MAX_CELLS / 2) * fine / 2
            val middle =
                GroundArea(
                    max(area.minX, camera.centerX - half),
                    max(area.minZ, camera.centerZ - half),
                    min(area.maxX, camera.centerX + half),
                    min(area.maxZ, camera.centerZ + half),
                )
            while (cells(area, coarse) > MAX_CELLS / 2) coarse *= 2
            request(inner, camera, middle, fine, tick, slice, max(coarse, CHUNK), INNER_PAD)
            request(outer, camera, area, coarse, tick, slice, align = null)
        }
        remesh(inner, Skips(voxels?.covered.orEmpty(), null), now)
        if (outer.wanted != null) remesh(outer, Skips(voxels?.covered.orEmpty(), inner.ground), now)
    }

    /** Asks [layer] for [area] at [step], read on a grid of [align] blocks and padded by [pad]. */
    @Suppress("LongParameterList")
    private fun request(
        layer: Layer,
        camera: IsoCamera,
        area: GroundArea,
        step: Int,
        tick: Long,
        slice: Int?,
        align: Int?,
        pad: Double = PAD,
    ) {
        // Overviews only change with commits; live refreshes are for tiles.
        val generation = if (tick == Long.MAX_VALUE && step <= CHUNK) generation else 0L
        val visible = layer.field
        val sameMoment =
            layer.shown?.let {
                it.tick == tick && it.generation == generation && it.slice == slice
            } == true
        if (
            visible != null &&
                sameMoment &&
                visible.covers(
                    area.minX.toInt(),
                    area.minZ.toInt(),
                    ceil(area.maxX).toInt(),
                    ceil(area.maxZ).toInt(),
                    step,
                )
        )
            return

        // Read a margin around the view, so panning a little does not read again.
        val cell = align ?: max(step, CHUNK)
        val padX = (area.maxX - area.minX) * pad
        val padZ = (area.maxZ - area.minZ) * pad
        var x0 = Math.floorDiv((area.minX - padX).toInt(), cell) * cell / CHUNK
        var z0 = Math.floorDiv((area.minZ - padZ).toInt(), cell) * cell / CHUNK
        var x1 = (Math.floorDiv(ceil(area.maxX + padX).toInt(), cell) + 1) * cell / CHUNK
        var z1 = (Math.floorDiv(ceil(area.maxZ + padZ).toInt(), cell) + 1) * cell / CHUNK
        val reach =
            when {
                slice != null -> MAX_SLICE_CHUNKS
                history == null -> MAX_LIVE_SIDE
                else -> null
            }
        if (reach != null) {
            val cx = Math.floorDiv(camera.centerX.toInt(), CHUNK)
            val cz = Math.floorDiv(camera.centerZ.toInt(), CHUNK)
            x0 = max(x0, cx - reach / 2)
            z0 = max(z0, cz - reach / 2)
            x1 = min(x1, cx + reach / 2)
            z1 = min(z1, cz + reach / 2)
        }
        val key = Key(ChunkWindow(x0, z0, x1 - x0, z1 - z0), step, tick, generation, slice)
        if (key == layer.wanted) return
        // A refresh of the same live window keeps the read in flight rather than restarting it.
        if (layer.wanted?.copy(generation = generation) == key && layer.cancelPending != null)
            return
        layer.cancelPending?.invoke()
        layer.wanted = key
        if (tick == Long.MAX_VALUE && layer === inner) watched = key.window
        layer.cancelPending = read(layer, key)
    }

    /** Starts reading [key] for [layer]; returns how to cancel it. */
    private fun read(layer: Layer, key: Key): () -> Unit {
        logger.debug("Reading {}", key)
        val window = key.window
        val snapshot = history?.dimension?.at(WorldTick(key.tick))
        val drawnId: (Int) -> Int = history?.let { it::drawnId } ?: { it }
        val (result: CompletableFuture<Heightfield>, cancel) =
            when {
                snapshot != null && key.step > CHUNK && key.slice == null -> {
                    val level = Integer.numberOfTrailingZeros(key.step / CHUNK)
                    val request = snapshot.overview(window, level)
                    request.result.thenApply { Heightfield.of(it, drawnId, live.shader) } to
                        request::cancel
                }
                snapshot != null &&
                    (key.slice != null ||
                        key.tick != Long.MAX_VALUE ||
                        window.width * window.height > MAX_LIVE_CHUNKS) -> {
                    val request =
                        if (key.slice != null) snapshot.ceiling(window, key.slice)
                        else snapshot.surface(window)
                    request.result.thenApply {
                        Heightfield.of(it, key.step, drawnId, live.shader)
                    } to request::cancel
                }
                else -> {
                    val future =
                        CompletableFuture.supplyAsync(
                            {
                                Heightfield.of(
                                    live.tiles(window.x0, window.z0, window.width, window.height),
                                    window.x0,
                                    window.z0,
                                    window.width,
                                    window.height,
                                    key.step,
                                    live.shader,
                                )
                            },
                            reader,
                        )
                    future to { future.cancel(false) }
                }
            }
        result
            .thenApplyAsync(
                { field ->
                    val skips =
                        Skips(
                            voxels?.covered.orEmpty(),
                            if (layer === outer) inner.ground else null,
                        )
                    Triple(field, skips, layer.meshing.meshes(field, skipper(field, skips)))
                },
                reader,
            )
            .thenAccept { (field, skips, meshes) -> land(layer, key, field, skips, meshes) }
            .exceptionally {
                if (it !is CancellationException && it.cause !is CancellationException)
                    logger.error("Could not read the map's terrain for {}", key, it)
                null
            }
        return { cancel() }
    }

    @Synchronized
    private fun land(
        layer: Layer,
        key: Key,
        field: Heightfield,
        skips: Skips,
        meshes: List<GpuSceneMesh>,
    ) {
        if (key != layer.wanted) return
        layer.shown = key
        layer.cancelPending = null
        layer.meshes = meshes
        layer.meshedSkips = skips
        layer.field = field
    }

    /**
     * When what [layer] leaves out changed — the ring filled in or moved, the inner layer landed —
     * meshes its shown field again; at most every [REMESH_NANOS], one at a time, on the reader.
     */
    private fun remesh(layer: Layer, skips: Skips, now: Long) {
        val field = layer.field ?: return
        val meshed = layer.meshedSkips
        if (meshed != null && meshed.covered === skips.covered && meshed.hole == skips.hole) return
        if (layer.remeshing || now - layer.remeshedAt < REMESH_NANOS) return
        layer.remeshing = true
        layer.remeshedAt = now
        reader.execute {
            try {
                val meshes = layer.meshing.meshes(field, skipper(field, skips))
                synchronized(this) {
                    if (layer.field === field) {
                        layer.meshes = meshes
                        layer.meshedSkips = skips
                    }
                }
            } finally {
                layer.remeshing = false
            }
        }
    }

    /** Which of [field]'s cells to leave out: under a covered chunk, or wholly in the hole. */
    private fun skipper(field: Heightfield, skips: Skips): (Int, Int) -> Boolean {
        val step = field.step
        val hole = skips.hole
        val covered = skips.covered
        if (hole == null && covered.isEmpty()) return { _, _ -> false }
        return { column, row ->
            val x = field.originX + column * step
            val z = field.originZ + row * step
            (hole != null &&
                x >= hole.minX &&
                z >= hole.minZ &&
                x + step <= hole.maxX &&
                z + step <= hole.maxZ) ||
                (covered.isNotEmpty() &&
                    VoxelRing.chunkKey(
                        Math.floorDiv(x + step / 2, CHUNK),
                        Math.floorDiv(z + step / 2, CHUNK),
                    ) in covered)
        }
    }

    override fun close() {
        live.removeChangeListener(onChange)
        inner.drop()
        outer.drop()
        voxels?.close()
    }

    private companion object {
        /** One background thread for every map; reads wait on the database. */
        val reader: ExecutorService = Executors.newSingleThreadExecutor {
            Thread(it, "palimpsest-iso").apply { isDaemon = true }
        }
        const val LIVE_REFRESH_NANOS = 1_000_000_000L
        /**
         * Live tiles are read again on every refresh; past this many chunks the view reads
         * history's surfaces, as of the last commit, instead.
         */
        const val MAX_LIVE_CHUNKS = 128 * 128
        /** Chunks a side of a live view that has no history to fall back on. */
        const val MAX_LIVE_SIDE = 128
        const val CHUNK = 16
        /** Cells narrower than this many pixels are merged; finer would only shimmer. */
        const val MIN_CELL_PIXELS = 0.5
        /** Columns a view draws at most, across its layers. */
        const val MAX_CELLS = 200_000.0
        const val MAX_SLICE_CHUNKS = 48
        const val VIEW_ABOVE = 96.0
        const val VIEW_BELOW = 64.0
        const val PAD = 0.25
        /** The middle layer's margin; it is a hole in the outer one, so kept small. */
        const val INNER_PAD = 0.1
        /** Blocks smaller than this many pixels are not worth drawing one by one. */
        const val VOXEL_PIXELS_PER_BLOCK = 1.0
        const val REMESH_NANOS = 250_000_000L

        fun cells(area: GroundArea, step: Int): Double =
            (area.maxX - area.minX) / step * ((area.maxZ - area.minZ) / step)
    }
}
