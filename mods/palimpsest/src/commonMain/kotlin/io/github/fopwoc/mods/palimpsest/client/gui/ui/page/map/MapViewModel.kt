package io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map

import androidx.lifecycle.ViewModel
import io.github.fopwoc.mods.framework.client.ClientBackend
import io.github.fopwoc.mods.framework.log.logger
import io.github.fopwoc.mods.framework.minecraft.ItemId
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuCanvasFrame
import io.github.fopwoc.mods.framework.ui.compose.canvas.GpuImageDraw
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimDraws
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimLayer
import io.github.fopwoc.mods.palimpsest.client.claim.ClaimMark
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.minimap.EntityDots
import io.github.fopwoc.mods.palimpsest.client.minimap.MINIMAP_MARKER_SIZE
import io.github.fopwoc.mods.palimpsest.client.minimap.MapTurn
import io.github.fopwoc.mods.palimpsest.client.minimap.PlayerMarker
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.map.MapCamera
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.tree.TileKey
import io.github.fopwoc.mods.palimpsest.waypoint.Waypoint
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Camera and snapshot selection of the map screen. Pointer, wheel and key input arrive as calls
 * from the screen; [advance] runs once per render frame and moves whatever is still gliding.
 */
class MapViewModel(private val session: MapSession, centerX: Double, centerZ: Double) :
    ViewModel() {
    private val logger = logger<MapViewModel>()
    private val camera = MapCameraMotion(centerX, centerZ)
    private val history = MapHistoryBrowser(session.map.store.tree)
    private val mapTime: MapTime
        get() = history.time

    private val mutableModel = MutableStateFlow(snapshot())
    private val mutableWaypointEditor = MutableStateFlow<WaypointEditorModel?>(null)
    private val mutableWaypointListOpen = MutableStateFlow(false)
    private var highlight: ChangeHighlight? = null
    private var viewportWidth = 1
    private var viewportHeight = 1
    private val entities = EntityDots()
    /** The player's own position, glided like the entities around them. */
    private var you: GlidingPoint? = null
    private val overlayClock = FrameClock()

    val model: StateFlow<MapModel> = mutableModel.asStateFlow()

    internal val waypointEditor: StateFlow<WaypointEditorModel?> =
        mutableWaypointEditor.asStateFlow()

    internal val waypointListOpen: StateFlow<Boolean> = mutableWaypointListOpen.asStateFlow()

    val waypoints: StateFlow<List<Waypoint>> = session.waypoints.entries
    val prospectingMarks: StateFlow<List<ProspectingMark>> = session.prospectingMarks
    val prospectingLayers: StateFlow<ProspectingLayers.Enabled> = ProspectingLayers.enabled
    val prospectingAvailable: Boolean = session.prospectingAvailable
    val nodeTrackingAvailable: Boolean = session.nodeTrackingAvailable
    val claimsAvailable: Boolean = session.claimsAvailable
    val claimMarks: StateFlow<List<ClaimMark>> = session.claimMarks
    val claimsEnabled: StateFlow<Boolean> = ClaimLayer.enabled

    fun claimFrame(width: Int, height: Int): GpuCanvasFrame =
        if (mapTime == MapTime.Live && ClaimLayer.enabled.value)
            ClaimDraws.frame(camera.camera(width, height), session.claimMarks.value)
        else GpuCanvasFrame(emptyList())

    val waypointPanelOpen: Boolean
        get() = mutableWaypointEditor.value != null || mutableWaypointListOpen.value

    val historyOpen: Boolean
        get() = history.open

    /** Draw commands for the current camera and moment; pages missing here are being built. */
    fun frame(width: Int, height: Int, nowNanos: Long): GpuCanvasFrame {
        val camera = camera.camera(width, height)
        val pages = session.map.view.frame(camera, mapTime)
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
            if (mapTime == MapTime.Live) entities.draws(camera, MapTurn.NONE, position.y, seconds)
            else emptyList<GpuImageDraw>().also { entities.clear() }
        return MapOverlay(GpuCanvasFrame(dots), GpuCanvasFrame(listOf(arrow)))
    }

    fun advance(frameNanos: Long, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        val moved = camera.advance(frameNanos, width, height)
        val glided = history.advance(frameNanos)
        if (moved || glided) publish()
        if (mapTime == MapTime.Live && ClaimLayer.enabled.value)
            session.requestClaims(camera.camera(width, height))
    }

    fun dragBy(dx: Double, dy: Double, nowNanos: Long) = update { camera.dragBy(dx, dy, nowNanos) }

    fun endDrag(nowNanos: Long) = camera.endDrag(nowNanos)

    fun panBy(dx: Double, dy: Double) = camera.panBy(dx, dy)

    fun lookAt(x: Double, z: Double) = camera.lookAt(x, z)

    fun addWaypointAtPlayer() {
        if (mapTime != MapTime.Live) return
        val player = ClientBackend.current.playerPosition ?: return
        mutableWaypointEditor.value =
            WaypointEditorModel.new(
                floor(player.x).toInt(),
                floor(player.y).toInt(),
                floor(player.z).toInt(),
            )
        mutableWaypointListOpen.value = false
    }

    fun openWaypointAt(screenX: Double, screenY: Double, width: Int, height: Int) {
        if (mapTime != MapTime.Live) return
        val camera = camera.camera(width, height)
        val nearby = waypointAt(camera, screenX, screenY)
        if (nearby != null) {
            mutableWaypointEditor.value = WaypointEditorModel.from(nearby)
            mutableWaypointListOpen.value = false
            return
        }
        val (worldX, worldZ) = camera.worldAt(screenX, screenY)
        val x = floor(worldX).toInt()
        val z = floor(worldZ).toInt()
        mutableWaypointEditor.value = WaypointEditorModel.new(x, surfaceYAt(x, z), z)
        mutableWaypointListOpen.value = false
    }

    fun editWaypointAt(screenX: Double, screenY: Double, width: Int, height: Int): Boolean {
        if (mapTime != MapTime.Live) return false
        val waypoint = waypointAt(camera.camera(width, height), screenX, screenY) ?: return false
        mutableWaypointEditor.value = WaypointEditorModel.from(waypoint)
        mutableWaypointListOpen.value = false
        return true
    }

    fun editWaypoint(id: UUID) {
        val waypoint = session.waypoints.entries.value.firstOrNull { it.id == id } ?: return
        mutableWaypointEditor.value = WaypointEditorModel.from(waypoint)
        mutableWaypointListOpen.value = false
        camera.lookAt(waypoint.x.toDouble(), waypoint.z.toDouble())
        publish()
    }

    fun closeWaypointEditor() {
        mutableWaypointEditor.value = null
    }

    fun toggleWaypointList() {
        if (mapTime != MapTime.Live) return
        mutableWaypointEditor.value = null
        mutableWaypointListOpen.value = !mutableWaypointListOpen.value
    }

    fun saveWaypoint(
        name: String,
        x: Int,
        y: Int,
        z: Int,
        icon: ItemId,
        tracked: Boolean,
    ): Boolean {
        val editor = mutableWaypointEditor.value ?: return false
        return runCatching {
            session.waypoints.save(Waypoint(editor.id, name.trim(), x, y, z, icon, tracked))
        }
            .onFailure { logger.error("Could not save waypoint {}", editor.id, it) }
            .isSuccess
            .also { if (it) closeWaypointEditor() }
    }

    fun deleteWaypoint(): Boolean {
        val editor = mutableWaypointEditor.value ?: return false
        if (editor.isNew) {
            closeWaypointEditor()
            return true
        }
        return runCatching { session.waypoints.delete(editor.id) }
            .onFailure { logger.error("Could not delete waypoint {}", editor.id, it) }
            .isSuccess
            .also { if (it) closeWaypointEditor() }
    }

    private fun waypointAt(
        camera: MapCamera,
        x: Double,
        y: Double,
    ): Waypoint? =
        session.waypoints.entries.value.firstOrNull { waypoint ->
            val (screenX, screenY) = camera.screenAt(waypoint.x + 0.5, waypoint.z + 0.5)
            abs(screenX - x) <= WAYPOINT_HIT_RADIUS && abs(screenY - y) <= WAYPOINT_HIT_RADIUS
        }

    private fun surfaceYAt(x: Int, z: Int): Int {
        val tile = session.map.store.latestTile(TileKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))
        val cell = Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)
        return tile?.height(cell)
            ?: ClientBackend.current.playerPosition?.y?.toInt()
            ?: session.ceiling
    }

    fun zoomBy(steps: Double, atX: Double, atY: Double) = camera.zoomBy(steps, atX, atY)

    fun openHistory() = update {
        closeWaypointEditor()
        mutableWaypointListOpen.value = false
        history.open()
    }

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
            time = mapTime,
            history =
                if (history.open) MapHistoryModel(history.labels, history.entry, history.position)
                else null,
        )

    private companion object {
        const val WAYPOINT_HIT_RADIUS = 10.0
        const val TILE_BLOCKS = 16.0
        /** Enough to outline a big commit; beyond it the flash would cost more than it tells. */
        const val MAX_HIGHLIGHT_TILES = 4096
        /** How much of the viewport the changed area fills after flying to it. */
        const val FIT_FRACTION = 0.6
        const val MAX_FIT_PIXELS_PER_BLOCK = 2.0
    }
}
