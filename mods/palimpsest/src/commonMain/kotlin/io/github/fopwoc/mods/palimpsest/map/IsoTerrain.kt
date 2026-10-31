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
 * What a view needs is a [Plan] of one or two fields. The shown plan keeps being drawn until every
 * field of the next one has been read; then both are meshed together, hole and all, and swap in at
 * once, so a change of detail never shows a gap. Fields are read and meshed on the reader thread,
 * reusing regions that did not change; a live view reads again when a tile in view changed, at most
 * every [liveRefreshNanos].
 */
class IsoTerrain(
    private val history: DimensionHistory?,
    private val live: LiveTiles,
    private val liveRefreshNanos: Long = LIVE_REFRESH_NANOS,
) : AutoCloseable {
    private val logger = logger<IsoTerrain>()
    private val voxels = history?.let { VoxelRing(it, live, live.shader) }
    private var voxelsActive = false
    private val innerMeshing = TerrainMeshes()
    private val outerMeshing = TerrainMeshes()
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

    /** The fields a view is drawn from: the middle, and maybe a coarser one around it. */
    private data class Plan(val inner: Key, val outer: Key?) {
        val keys: List<Key>
            get() = listOfNotNull(inner, outer)
    }

    /** A rectangle of whole blocks, [minX] and [minZ] in, [maxX] and [maxZ] out. */
    private data class Hole(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int)

    /** What is on screen: a plan's fields, meshed around the [covered] chunks. */
    private class Shown(
        val plan: Plan,
        val inner: Heightfield,
        val outer: Heightfield?,
        val covered: Set<Long>,
        val meshes: List<GpuSceneMesh>,
    )

    @Volatile private var shown: Shown? = null
    private var wanted: Plan? = null
    /** Fields read for the wanted plan, not shown yet. */
    private val landed = HashMap<Key, Heightfield>()
    private val reads = HashMap<Key, () -> Unit>()
    @Volatile private var composing = false
    private var composedAt = 0L

    /** The top of the ground at a block column, finest field first; null where none was read. */
    fun topAt(x: Int, z: Int): Int? = shown?.let { it.inner.topAt(x, z) ?: it.outer?.topAt(x, z) }

    /** The columns and the ring's blocks as world-space meshes for the GPU. */
    val meshes: List<GpuSceneMesh>
        get() = shown?.meshes.orEmpty() + voxels?.meshes.orEmpty()

    /**
     * Asks for whatever [camera] needs at [time] cut at [slice]; cheap when it is already there.
     * Without history only the live, uncut world can be shown.
     */
    @Synchronized
    fun request(camera: IsoCamera, time: MapTime, slice: Int?) {
        val slice = slice.takeIf { history != null }
        val tick = (time as? MapTime.At)?.tick?.takeIf { history != null } ?: Long.MAX_VALUE
        val now = System.nanoTime()
        if (
            tick == Long.MAX_VALUE &&
                changes != seenChanges &&
                now - refreshedAt >= liveRefreshNanos
        ) {
            seenChanges = changes
            refreshedAt = now
            generation++
        }
        // A zoom gliding across the threshold should not switch the ring on and off.
        voxelsActive =
            camera.pixelsPerBlock >=
                if (voxelsActive) VOXEL_PIXELS_PER_BLOCK * VOXEL_HYSTERESIS
                else VOXEL_PIXELS_PER_BLOCK
        voxels?.request(camera.centerX, camera.centerZ, tick, slice, voxelsActive)
        plan(camera, tick, slice)?.let(::want)
        // The ring filled in, moved or let go: mesh what is shown around it again.
        val shown = shown
        if (shown != null && shown.covered !== covered() && wanted == shown.plan)
            compose(shown.plan, shown.inner, shown.outer, now)
    }

    /** The chunks the columns leave to the ring: none while it is off. */
    private fun covered(): Set<Long> = voxels?.takeIf { voxelsActive }?.covered.orEmpty()

    /** The fields [camera] needs, reusing shown ones that still cover their part of the view. */
    private fun plan(camera: IsoCamera, tick: Long, slice: Int?): Plan? {
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
            return Plan(key(camera, area, step, tick, slice, align = null, pad = PAD), null)
        }
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
        return Plan(
            key(camera, middle, fine, tick, slice, max(coarse, CHUNK), INNER_PAD),
            key(camera, area, coarse, tick, slice, align = null, pad = PAD),
        )
    }

    /**
     * The field for [area] at [step]: a shown or wanted one if it still covers it at the same
     * moment, else one read on a grid of [align] blocks and padded by [pad].
     */
    @Suppress("LongParameterList")
    private fun key(
        camera: IsoCamera,
        area: GroundArea,
        step: Int,
        tick: Long,
        slice: Int?,
        align: Int?,
        pad: Double,
    ): Key {
        // Overviews only change with commits; live refreshes are for tiles.
        val generation = if (tick == Long.MAX_VALUE && step <= CHUNK) generation else 0L
        val known = buildList {
            shown?.let {
                add(it.plan.inner to it.inner)
                if (it.plan.outer != null && it.outer != null) add(it.plan.outer to it.outer)
            }
            landed.forEach { (key, field) -> add(key to field) }
        }
        known
            .firstOrNull { (key, field) ->
                key.step == step &&
                    key.tick == tick &&
                    key.generation == generation &&
                    key.slice == slice &&
                    field.covers(
                        area.minX.toInt(),
                        area.minZ.toInt(),
                        ceil(area.maxX).toInt(),
                        ceil(area.maxZ).toInt(),
                        step,
                    )
            }
            ?.let {
                return it.first
            }
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
        // A refresh of a window already being read keeps that read rather than restarting it.
        return reads.keys.firstOrNull { it.copy(generation = generation) == key } ?: key
    }

    /** Makes [plan] the one to show next: reads what it lacks, drops reads it no longer needs. */
    private fun want(plan: Plan) {
        if (plan == wanted) return
        wanted = plan
        if (plan.inner.tick == Long.MAX_VALUE) watched = plan.inner.window
        val needed = plan.keys.toSet()
        reads.entries.removeIf { (key, cancel) -> (key !in needed).also { if (it) cancel() } }
        landed.keys.retainAll(needed)
        val shown = shown
        for (key in plan.keys) {
            val available =
                key in landed ||
                    key in reads ||
                    key == shown?.plan?.inner ||
                    (key == shown?.plan?.outer && shown.outer != null)
            if (!available) reads[key] = read(key)
        }
        ready()
    }

    /** Composes the wanted plan once every field of it is at hand. */
    private fun ready() {
        val plan = wanted ?: return
        val shown = shown
        fun field(key: Key): Heightfield? =
            landed[key]
                ?: shown?.let {
                    when (key) {
                        it.plan.inner -> it.inner
                        it.plan.outer -> it.outer
                        else -> null
                    }
                }
        val inner = field(plan.inner) ?: return
        val outer = plan.outer?.let { field(it) ?: return }
        if (shown?.plan == plan) return
        compose(plan, inner, outer, System.nanoTime(), throttle = false)
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
            .thenAccept { field -> land(key, field) }
            .exceptionally {
                if (it !is CancellationException && it.cause !is CancellationException)
                    logger.error("Could not read the map's terrain for {}", key, it)
                null
            }
        return { cancel() }
    }

    @Synchronized
    private fun land(key: Key, field: Heightfield) {
        if (reads.remove(key) == null) return
        landed[key] = field
        ready()
    }

    /**
     * Meshes [plan]'s fields around the chunks the ring covers now, the outer one around the inner,
     * and shows them together; on the reader thread, one at a time, and when it only catches up
     * with the ring at most every [REMESH_NANOS].
     */
    private fun compose(
        plan: Plan,
        inner: Heightfield,
        outer: Heightfield?,
        now: Long,
        throttle: Boolean = true,
    ) {
        if (composing && throttle) return
        if (throttle && now - composedAt < REMESH_NANOS) return
        composing = true
        composedAt = now
        val covered = covered()
        reader.execute {
            try {
                val meshes =
                    innerMeshing.meshes(inner, skipper(inner, covered, null)) +
                        outer
                            ?.let { outerMeshing.meshes(it, skipper(it, covered, ground(inner))) }
                            .orEmpty()
                show(Shown(plan, inner, outer, covered, meshes))
            } finally {
                composing = false
            }
        }
    }

    @Synchronized
    private fun show(next: Shown) {
        // A plan given up on while it was meshed waits for the one wanted now.
        if (next.plan != wanted) return
        shown = next
        landed.keys.removeAll(next.plan.keys.toSet())
        // The columns cover the ring's ground again; only now may it let go.
        if (!voxelsActive && next.covered.isEmpty()) voxels?.release()
    }

    private fun ground(field: Heightfield) =
        Hole(
            field.originX,
            field.originZ,
            field.originX + field.columns * field.step,
            field.originZ + field.rows * field.step,
        )

    /** Which of [field]'s cells to leave out: under a covered chunk, or wholly in [hole]. */
    private fun skipper(
        field: Heightfield,
        covered: Set<Long>,
        hole: Hole?,
    ): (Int, Int) -> Boolean {
        val step = field.step
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

    @Synchronized
    override fun close() {
        live.removeChangeListener(onChange)
        reads.values.forEach { it() }
        reads.clear()
        landed.clear()
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
        /** Once on, the ring stays on until blocks shrink this much further. */
        const val VOXEL_HYSTERESIS = 0.75
        const val REMESH_NANOS = 250_000_000L

        fun cells(area: GroundArea, step: Int): Double =
            (area.maxX - area.minX) / step * ((area.maxZ - area.minZ) / step)
    }
}
