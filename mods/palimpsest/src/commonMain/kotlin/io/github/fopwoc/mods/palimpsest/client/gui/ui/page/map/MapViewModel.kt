package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.minimap.EntityDots
import io.github.fopwoc.mods.palimpsest.client.minimap.MINIMAP_MARKER_SIZE
import io.github.fopwoc.mods.palimpsest.client.minimap.MapTurn
import io.github.fopwoc.mods.palimpsest.client.minimap.PlayerMarker
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.map.MapTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera and snapshot selection of the map screen. Pointer, wheel and key input arrive as calls
 * from the screen; [advance] runs once per render frame and moves whatever is still gliding.
 */
class MapViewModel(private val session: MapSession, centerX: Double, centerZ: Double) :
    ViewModel() {
    private val camera = MapCameraMotion(centerX, centerZ)
    private val history = MapHistoryBrowser(session.map.store.tree)
    private val mutableModel = MutableStateFlow(snapshot())
    private var highlight: ChangeHighlight? = null
    private var viewportWidth = 1
    private var viewportHeight = 1
    private val entities = EntityDots()
    /** The player's own position, glided like the entities around them. */
    private var you: GlidingPoint? = null
    private val overlayClock = FrameClock()

    val model: StateFlow<MapModel> = mutableModel.asStateFlow()

    val historyOpen: Boolean
        get() = history.open

    /** Draw commands for the current camera and moment; pages missing here are being built. */
    fun frame(width: Int, height: Int, nowNanos: Long): GpuCanvasFrame {
        val camera = camera.camera(width, height)
        val pages = session.map.view.frame(camera, history.time)
        val flash = highlight?.takeIf { it.visible(nowNanos) } ?: return pages
        return GpuCanvasFrame(pages.draws + flash.draws(camera, nowNanos))
    }

    /**
     * Where the player is and looks, as an arrow, and while the map is live the entities loaded
     * around them as dots; both empty when the map shows another world or dimension.
     */
    fun overlay(width: Int, height: Int, nowNanos: Long): MapOverlay {
        val seconds = overlayClock.tick(nowNanos)
        val client = ClientBackend.current
        val position = client.playerPosition
        if (MapSessions.session !== session || position == null) return MapOverlay.EMPTY
        val camera = camera.camera(width, height)
        val self =
            you?.also { it.follow(position.x, position.y, position.z, seconds) }
                ?: GlidingPoint(position.x, position.y, position.z).also { you = it }
        val size = MINIMAP_MARKER_SIZE.toFloat()
        val arrow =
            GpuImageDraw(
                PlayerMarker.image,
                ((self.x - camera.centerX) * camera.pixelsPerBlock + width / 2.0 - size / 2)
                    .toFloat(),
                ((self.z - camera.centerZ) * camera.pixelsPerBlock + height / 2.0 - size / 2)
                    .toFloat(),
                size,
                size,
                PlayerMarker.rotation(client.playerYaw ?: 0f),
            )
        val dots =
            if (history.time == MapTime.Live)
                entities.draws(camera, MapTurn.NONE, position.y, seconds)
            else emptyList<GpuImageDraw>().also { entities.clear() }
        return MapOverlay(GpuCanvasFrame(dots), GpuCanvasFrame(listOf(arrow)))
    }

    fun advance(frameNanos: Long, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        val moved = camera.advance(frameNanos, width, height)
        val glided = history.advance(frameNanos)
        if (moved || glided) publish()
    }

    fun dragBy(dx: Double, dy: Double, nowNanos: Long) = update { camera.dragBy(dx, dy, nowNanos) }

    fun endDrag(nowNanos: Long) = camera.endDrag(nowNanos)

    fun panBy(dx: Double, dy: Double) = camera.panBy(dx, dy)

    fun lookAt(x: Double, z: Double) = camera.lookAt(x, z)

    fun zoomBy(steps: Double, atX: Double, atY: Double) = camera.zoomBy(steps, atX, atY)

    fun openHistory() = update { history.open() }

    fun closeHistory() = update { history.close() }

    fun stepHistory(delta: Int) = update {
        history.step(delta)
        showChange()
    }

    fun selectSnapshot(entry: Int) = update {
        history.select(entry)
        showChange()
    }

    /** Flies to what the selected snapshot changed and flashes it. */
    private fun showChange() {
        val tiles = history.changedTiles(MAX_HIGHLIGHT_TILES)
        if (tiles.isEmpty()) {
            highlight = null
            return
        }
        highlight = ChangeHighlight(tiles, System.nanoTime())
        val minX = tiles.minOf { it.x } * TILE_BLOCKS
        val maxX = (tiles.maxOf { it.x } + 1) * TILE_BLOCKS
        val minZ = tiles.minOf { it.z } * TILE_BLOCKS
        val maxZ = (tiles.maxOf { it.z } + 1) * TILE_BLOCKS
        val fit =
            (FIT_FRACTION * minOf(viewportWidth / (maxX - minX), viewportHeight / (maxZ - minZ)))
                .coerceAtMost(MAX_FIT_PIXELS_PER_BLOCK)
        camera.flyTo((minX + maxX) / 2, (minZ + maxZ) / 2, fit)
    }

    fun historyScrolledTo(rowPosition: Double) = update { history.adopt(rowPosition) }

    private inline fun update(change: () -> Unit) {
        change()
        publish()
    }

    private fun publish() {
        mutableModel.value = snapshot()
    }

    private fun snapshot() =
        MapModel(
            centerX = camera.centerX,
            centerZ = camera.centerZ,
            pixelsPerBlock = camera.pixelsPerBlock,
            time = history.time,
            history =
                if (history.open) MapHistoryModel(history.labels, history.entry, history.position)
                else null,
        )

    private companion object {
        const val TILE_BLOCKS = 16.0
        /** Enough to outline a big commit; beyond it the flash would cost more than it tells. */
        const val MAX_HIGHLIGHT_TILES = 4096
        /** How much of the viewport the changed area fills after flying to it. */
        const val FIT_FRACTION = 0.6
        const val MAX_FIT_PIXELS_PER_BLOCK = 2.0
    }
}
