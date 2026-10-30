package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.map.MapPageKey
import io.github.fopwoc.mods.palimpsest.map.MapPageStore
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.render.iso.Heightfield
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
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
 * The columns the 3D view stands on, read around what the camera sees: near by from the same tiles
 * as the flat map, live ones with what the scan saw since the last commit; far out from the
 * overview; with a [slice], the world seen from that height down. History has those only as of its
 * last commit. One read is in flight at a time; until it lands the previous field keeps being
 * drawn, and a live view reads again when the map changed, at most every [LIVE_REFRESH_NANOS].
 */
class IsoTerrain(private val history: DimensionHistory, private val store: MapPageStore) :
    AutoCloseable {
    private val logger = logger<IsoTerrain>()
    private val shader = store.shader
    @Volatile private var changes = 0L
    private val onChange: (Collection<MapPageKey>) -> Unit = { changes++ }
    private var seenChanges = 0L
    private var generation = 0L
    private var refreshedAt = 0L

    init {
        store.addInvalidationListener(onChange)
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

    private var shown: Key? = null
    private var wanted: Key? = null
    private var cancelPending: (() -> Unit)? = null

    /**
     * Asks for whatever [camera] needs at [time] cut at [slice]; cheap when it is already there.
     */
    @Synchronized
    fun request(camera: IsoCamera, time: MapTime, slice: Int?) {
        val tick = (time as? MapTime.At)?.tick ?: Long.MAX_VALUE
        val now = System.nanoTime()
        val live = time == MapTime.Live
        if (live && changes != seenChanges && now - refreshedAt >= LIVE_REFRESH_NANOS) {
            seenChanges = changes
            refreshedAt = now
            generation++
        }
        val generation = if (live) generation else 0L
        // What the canvas sees of the ground between the slice, or a generous sky, and the floor.
        val high = (slice ?: (camera.centerY + VIEW_ABOVE).toInt()).toDouble()
        val low = camera.centerY - VIEW_BELOW
        var minX = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (sx in doubleArrayOf(0.0, camera.width.toDouble())) for (sy in
            doubleArrayOf(0.0, camera.height.toDouble())) for (y in doubleArrayOf(low, high)) {
            val (x, z) = camera.groundAt(sx, sy, y)
            minX = min(minX, x)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxZ = max(maxZ, z)
        }
        var step = 1
        while (step < MIN_CELL_PIXELS / camera.pixelsPerBlock) step *= 2
        while ((maxX - minX) / step * ((maxZ - minZ) / step) > MAX_CELLS) step *= 2
        if (slice != null) step = step.coerceAtMost(CHUNK)
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
        if (slice != null) {
            // Ceilings are decoded from history chunk by chunk; keep them to the middle of the
            // view.
            val cx = Math.floorDiv(camera.centerX.toInt(), CHUNK)
            val cz = Math.floorDiv(camera.centerZ.toInt(), CHUNK)
            x0 = max(x0, cx - MAX_SLICE_CHUNKS / 2)
            z0 = max(z0, cz - MAX_SLICE_CHUNKS / 2)
            x1 = min(x1, cx + MAX_SLICE_CHUNKS / 2)
            z1 = min(z1, cz + MAX_SLICE_CHUNKS / 2)
        }
        val key = Key(ChunkWindow(x0, z0, x1 - x0, z1 - z0), step, tick, generation, slice)
        if (key == wanted) return
        // A refresh of the same live window keeps the read in flight rather than restarting it.
        if (wanted?.copy(generation = generation) == key && cancelPending != null) return
        cancelPending?.invoke()
        wanted = key
        cancelPending = read(key)
    }

    /** Starts reading [key]; returns how to cancel it. */
    private fun read(key: Key): () -> Unit {
        logger.debug("Reading {}", key)
        val window = key.window
        val snapshot = history.dimension.at(WorldTick(key.tick))
        val (result: CompletableFuture<Heightfield>, cancel) =
            when {
                key.step > CHUNK && key.slice == null -> {
                    val level = Integer.numberOfTrailingZeros(key.step / CHUNK)
                    val request = snapshot.overview(window, level)
                    request.result.thenApply { Heightfield.of(it, history::drawnId, shader) } to
                        request::cancel
                }
                key.slice != null ||
                    key.tick != Long.MAX_VALUE ||
                    window.width * window.height > MAX_LIVE_CHUNKS -> {
                    val request =
                        if (key.slice != null) snapshot.ceiling(window, key.slice)
                        else snapshot.surface(window)
                    request.result.thenApply {
                        Heightfield.of(it, key.step, history::drawnId, shader)
                    } to request::cancel
                }
                else -> {
                    val future =
                        CompletableFuture.supplyAsync(
                            {
                                Heightfield.of(
                                    store.latestTiles(
                                        window.x0,
                                        window.z0,
                                        window.width,
                                        window.height,
                                    ),
                                    window.x0,
                                    window.z0,
                                    window.width,
                                    window.height,
                                    key.step,
                                    shader,
                                )
                            },
                            reader,
                        )
                    future to { future.cancel(false) }
                }
            }
        result
            .thenAccept { land(key, it) }
            .exceptionally {
                if (it !is CancellationException && it.cause !is CancellationException)
                    logger.error("Could not read the 3D view's terrain for {}", key, it)
                null
            }
        return { cancel() }
    }

    @Synchronized
    private fun land(key: Key, field: Heightfield) {
        if (key != wanted) return
        shown = key
        cancelPending = null
        this.field = field
    }

    override fun close() {
        store.removeInvalidationListener(onChange)
        cancelPending?.invoke()
    }

    private companion object {
        /** One background thread for every map screen; reads wait on the database. */
        val reader: ExecutorService = Executors.newSingleThreadExecutor {
            Thread(it, "palimpsest-iso").apply { isDaemon = true }
        }
        const val LIVE_REFRESH_NANOS = 3_000_000_000L
        /**
         * Live tiles are rebuilt from history and the scan on every refresh; past this many chunks
         * the view reads history's surfaces, as of the last commit, instead.
         */
        const val MAX_LIVE_CHUNKS = 128 * 128
        const val CHUNK = 16
        /** Cells narrower than this are merged: the view would only shimmer. */
        const val MIN_CELL_PIXELS = 3.0
        const val MAX_CELLS = 160_000.0
        const val MAX_SLICE_CHUNKS = 48
        const val VIEW_ABOVE = 96.0
        const val VIEW_BELOW = 64.0
        const val PAD = 0.25
    }
}
