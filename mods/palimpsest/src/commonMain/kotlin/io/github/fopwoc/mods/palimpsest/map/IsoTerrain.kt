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

/**
 * The columns a map view stands on, read around what the camera sees: near by from the [live]
 * tiles, with what the scan saw since the last commit; with a [history], far out from its overview,
 * at past moments, and with a slice, the world seen from that height down, all as of its last
 * commit. Without one the view is live and reaches only [MAX_LIVE_CHUNKS] around its centre. Each
 * field is meshed on the reader thread, reusing regions that did not change. One read is in flight
 * at a time; until it lands the previous field keeps being drawn, and a live view reads again when
 * a tile in view changed, at most every [liveRefreshNanos]. Close enough to make out blocks, the
 * middle of a view with history is drawn from whole chunks by a [VoxelRing], and the columns step
 * aside under it.
 */
class IsoTerrain(
    private val history: DimensionHistory?,
    private val live: LiveTiles,
    private val liveRefreshNanos: Long = LIVE_REFRESH_NANOS,
) : AutoCloseable {
    private val logger = logger<IsoTerrain>()
    private val meshing = TerrainMeshes()
    private val voxels = history?.let { VoxelRing(it, live, live.shader) }
    /** The covered chunks the columns were last meshed around, and when. */
    @Volatile private var meshedCovered: Set<Long> = emptySet()
    private var remeshedAt = 0L
    @Volatile private var remeshing = false
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

    @Volatile
    var field: Heightfield? = null
        private set

    @Volatile private var columnMeshes: List<GpuSceneMesh> = emptyList()

    /** [field]'s columns and the ring's blocks as world-space meshes for the GPU. */
    val meshes: List<GpuSceneMesh>
        get() = voxels?.meshes?.let { columnMeshes + it } ?: columnMeshes

    private var shown: Key? = null
    private var wanted: Key? = null
    private var cancelPending: (() -> Unit)? = null

    /**
     * Asks for whatever [camera] needs at [time] cut at [slice]; cheap when it is already there.
     * Without history only the live, uncut world can be shown.
     */
    @Synchronized
    fun request(camera: IsoCamera, time: MapTime, slice: Int?) {
        val slice = slice.takeIf { history != null }
        val tick = (time as? MapTime.At)?.tick?.takeIf { history != null } ?: Long.MAX_VALUE
        val now = System.nanoTime()
        val live = tick == Long.MAX_VALUE
        if (live && changes != seenChanges && now - refreshedAt >= liveRefreshNanos) {
            seenChanges = changes
            refreshedAt = now
            generation++
        }
        val generation = if (live) generation else 0L
        // What the canvas sees of the ground between the slice, or a generous sky, and the floor.
        val area =
            camera.footprint(
                camera.centerY - VIEW_BELOW,
                (slice ?: (camera.centerY + VIEW_ABOVE).toInt()).toDouble(),
            )
        val minX = area.minX
        val minZ = area.minZ
        val maxX = area.maxX
        val maxZ = area.maxZ
        voxels?.request(
            camera.centerX,
            camera.centerZ,
            tick,
            slice,
            camera.pixelsPerBlock >= VOXEL_PIXELS_PER_BLOCK,
        )
        remeshAroundVoxels(now)
        var step = 1
        while (step < MIN_CELL_PIXELS / camera.pixelsPerBlock) step *= 2
        while ((maxX - minX) / step * ((maxZ - minZ) / step) > MAX_CELLS) step *= 2
        if (slice != null || history == null) step = step.coerceAtMost(CHUNK)
        val visible = field
        val sameMoment =
            shown?.let { it.tick == tick && it.generation == generation && it.slice == slice } ==
                true
        if (
            visible != null &&
                sameMoment &&
                visible.covers(
                    minX.toInt(),
                    minZ.toInt(),
                    ceil(maxX).toInt(),
                    ceil(maxZ).toInt(),
                    step,
                )
        )
            return

        // Read a margin around the view, so panning a little does not read again.
        val cell = max(step, CHUNK)
        val padX = (maxX - minX) * PAD
        val padZ = (maxZ - minZ) * PAD
        var x0 = Math.floorDiv((minX - padX).toInt(), cell) * cell / CHUNK
        var z0 = Math.floorDiv((minZ - padZ).toInt(), cell) * cell / CHUNK
        var x1 = (Math.floorDiv(ceil(maxX + padX).toInt(), cell) + 1) * cell / CHUNK
        var z1 = (Math.floorDiv(ceil(maxZ + padZ).toInt(), cell) + 1) * cell / CHUNK
        // Ceilings are decoded from history chunk by chunk, and without history every tile is
        // read live; either way, keep to the middle of the view.
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
        if (key == wanted) return
        // A refresh of the same live window keeps the read in flight rather than restarting it.
        if (wanted?.copy(generation = generation) == key && cancelPending != null) return
        cancelPending?.invoke()
        wanted = key
        if (live) watched = key.window
        cancelPending = read(key)
    }

    /** Starts reading [key]; returns how to cancel it. */
    private fun read(key: Key): () -> Unit {
        logger.debug("Reading {}", key)
        val window = key.window
        val snapshot = history?.dimension?.at(WorldTick(key.tick))
        val drawnId: (Int) -> Int = history?.let { it::drawnId } ?: { it }
        val (result: CompletableFuture<Heightfield>, cancel) =
            when {
                snapshot != null && key.step > CHUNK && key.slice == null -> {
                    val level = Integer.numberOfTrailingZeros(key.step / CHUNK)
                    val request = snapshot.overview(window, level)
                    request.result.thenApply {
                        Heightfield.of(it, drawnId, live.shader)
                    } to request::cancel
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
                    val covered = voxels?.covered.orEmpty()
                    meshedCovered = covered
                    field to meshing.meshes(field, covered)
                },
                reader,
            )
            .thenAccept { (field, meshes) -> land(key, field, meshes) }
            .exceptionally {
                if (it !is CancellationException && it.cause !is CancellationException)
                    logger.error("Could not read the map's terrain for {}", key, it)
                null
            }
        return { cancel() }
    }

    @Synchronized
    private fun land(key: Key, field: Heightfield, meshes: List<GpuSceneMesh>) {
        if (key != wanted) return
        shown = key
        cancelPending = null
        columnMeshes = meshes
        this.field = field
    }

    /**
     * As the ring fills in or moves, meshes the shown field's columns again without the chunks it
     * now covers; at most every [REMESH_NANOS], one at a time, on the reader thread.
     */
    private fun remeshAroundVoxels(now: Long) {
        val voxels = voxels ?: return
        val field = field ?: return
        val covered = voxels.covered
        if (covered === meshedCovered || remeshing || now - remeshedAt < REMESH_NANOS) return
        remeshing = true
        remeshedAt = now
        reader.execute {
            try {
                val meshes = meshing.meshes(field, covered)
                synchronized(this) {
                    if (this.field === field) {
                        columnMeshes = meshes
                        meshedCovered = covered
                    }
                }
            } finally {
                remeshing = false
            }
        }
    }

    override fun close() {
        live.removeChangeListener(onChange)
        cancelPending?.invoke()
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
        const val MAX_CELLS = 160_000.0
        const val MAX_SLICE_CHUNKS = 48
        const val VIEW_ABOVE = 96.0
        const val VIEW_BELOW = 64.0
        const val PAD = 0.25
        /** Blocks smaller than this many pixels are not worth drawing one by one. */
        const val VOXEL_PIXELS_PER_BLOCK = 1.0
        const val REMESH_NANOS = 250_000_000L
    }
}
