package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso

import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.palimpsest.history.DimensionHistory
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.render.TerrainShader
import io.github.fopwoc.mods.palimpsest.render.iso.Heightfield
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
import io.github.fopwoc.palimpsest.db.ChunkWindow
import io.github.fopwoc.palimpsest.db.Request
import io.github.fopwoc.palimpsest.db.WorldTick
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The columns the 3D view stands on, read from history around what the camera sees: surfaces near
 * by, the overview far out, and with a [slice] the world seen from that height down. One read is in
 * flight at a time; until it lands the previous field keeps being drawn. Live means the latest
 * commit, so the view trails the flat map by up to one commit interval.
 */
class IsoTerrain(private val history: DimensionHistory, private val shader: TerrainShader) {
    private val logger = logger<IsoTerrain>()

    private data class Key(val window: ChunkWindow, val step: Int, val tick: Long, val slice: Int?)

    @Volatile
    var field: Heightfield? = null
        private set

    private var shown: Key? = null
    private var wanted: Key? = null
    private var pending: Request<*>? = null

    /**
     * Asks for whatever [camera] needs at [time] cut at [slice]; cheap when it is already there.
     */
    @Synchronized
    fun request(camera: IsoCamera, time: MapTime, slice: Int?) {
        val tick =
            when (time) {
                MapTime.Live -> history.dimension.latest?.tick?.value ?: return
                is MapTime.At -> time.tick
            }
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
        val current = shown
        if (
            visible != null &&
                current != null &&
                current.tick == tick &&
                current.slice == slice &&
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
        val key = Key(ChunkWindow(x0, z0, x1 - x0, z1 - z0), step, tick, slice)
        if (key == wanted) return
        pending?.cancel()
        wanted = key
        pending = read(key)
    }

    private fun read(key: Key): Request<*> {
        val snapshot = history.dimension.at(WorldTick(key.tick))
        logger.debug("Reading {}", key)
        val request =
            if (key.step > CHUNK && key.slice == null) {
                val level = Integer.numberOfTrailingZeros(key.step / CHUNK)
                snapshot.overview(key.window, level).also { request ->
                    request.result.thenAccept {
                        land(key, Heightfield.of(it, history::drawnId, shader))
                    }
                }
            } else {
                val surface =
                    if (key.slice == null) snapshot.surface(key.window)
                    else snapshot.ceiling(key.window, key.slice)
                surface.also { request ->
                    request.result.thenAccept {
                        land(key, Heightfield.of(it, key.step, history::drawnId, shader))
                    }
                }
            }
        request.result.exceptionally {
            if (it !is CancellationException)
                logger.error("Could not read the 3D view's terrain for {}", key, it)
            null
        }
        return request
    }

    @Synchronized
    private fun land(key: Key, field: Heightfield) {
        if (key != wanted) return
        shown = key
        this.field = field
    }

    private companion object {
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
