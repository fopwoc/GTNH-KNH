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
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.CubeTarget
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.IsoTerrain
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.MapOrbit
import io.github.fopwoc.mods.palimpsest.client.gui.ui.page.map.iso.ViewCube
import io.github.fopwoc.mods.palimpsest.client.map.MapSession
import io.github.fopwoc.mods.palimpsest.client.map.MapSessions
import io.github.fopwoc.mods.palimpsest.client.minimap.EntityDots
import io.github.fopwoc.mods.palimpsest.client.minimap.MINIMAP_MARKER_SIZE
import io.github.fopwoc.mods.palimpsest.client.minimap.MapTurn
import io.github.fopwoc.mods.palimpsest.client.minimap.PlayerMarker
import io.github.fopwoc.mods.palimpsest.client.motion.FrameClock
import io.github.fopwoc.mods.palimpsest.client.motion.GlidingPoint
import io.github.fopwoc.mods.palimpsest.client.motion.easeStep
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingLayers
import io.github.fopwoc.mods.palimpsest.client.prospecting.ProspectingMark
import io.github.fopwoc.mods.palimpsest.map.MapTime
import io.github.fopwoc.mods.palimpsest.render.iso.ColumnMesher
import io.github.fopwoc.mods.palimpsest.render.iso.IsoCamera
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
    private val history = MapHistoryBrowser(session.history?.dimension)
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
    private val orbit = MapOrbit()
    private val orbitClock = FrameClock(MAX_ORBIT_SECONDS)
    private val terrain = session.history?.let { IsoTerrain(it, session.map.store.shader) }
    /** The height in the middle of the 3D view, glided after the ground under the centre. */
    private var centerY = ClientBackend.current.playerPosition?.y ?: session.ceiling / 2.0
    val sliceRange = (if (session.ceiling > VANILLA_TOP) MODERN_BOTTOM else 0)..session.ceiling
    private var mesh: Pair<List<Any>, GpuCanvasFrame>? = null
    private var hoveredFace: CubeTarget? = null
    private var cube: Pair<List<Any?>, GpuCanvasFrame>? = null

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
        if (mapTime == MapTime.Live && flat && ClaimLayer.enabled.value)
            ClaimDraws.frame(camera.camera(width, height), session.claimMarks.value)
        else GpuCanvasFrame(emptyList())

    val waypointPanelOpen: Boolean
        get() = mutableWaypointEditor.value != null || mutableWaypointListOpen.value

    val historyOpen: Boolean
        get() = history.open

    /** Whether the world has history to stand the 3D view's columns on. */
    val viewAvailable: Boolean
        get() = terrain != null

    /** Whether the map is drawn flat; the 3D view needs history to stand its columns on. */
    private val flat: Boolean
        get() = terrain == null || orbit.isFlat

    private fun isoCamera(width: Int, height: Int) =
        IsoCamera(
            camera.centerX,
            centerY,
            camera.centerZ,
            camera.pixelsPerBlock,
            orbit.yaw,
            orbit.pitch,
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
        )

    /** Draw commands for the current camera and moment; pages missing here are being built. */
    fun frame(width: Int, height: Int, nowNanos: Long): GpuCanvasFrame {
        if (!flat) return isoFrame(width, height)
        val camera = camera.camera(width, height)
        val pages = session.map.view.frame(camera, mapTime)
        val flash = highlight?.takeIf { it.visible(nowNanos) } ?: return pages
        return GpuCanvasFrame(pages.draws + flash.draws(camera, nowNanos))
    }

    /** The columns as triangles, rebuilt only when the field or the camera changed. */
    private fun isoFrame(width: Int, height: Int): GpuCanvasFrame {
        val iso = isoCamera(width, height)
        terrain!!.request(iso, mapTime, orbit.slice)
        val field = terrain.field ?: return GpuCanvasFrame(emptyList())
        val key =
            listOf(
                field,
                iso.centerX,
                iso.centerY,
                iso.centerZ,
                iso.pixelsPerBlock,
                iso.yaw,
                iso.pitch,
                width,
                height,
            )
        mesh
            ?.takeIf { it.first == key }
            ?.let {
                return it.second
            }
        return GpuCanvasFrame(emptyList(), ColumnMesher.mesh(field, iso)).also { mesh = key to it }
    }

    /** The view cube at the map's angle, redrawn only when the angle or the hover changes. */
    fun cubeFrame(): GpuCanvasFrame {
        val key = listOf(orbit.yaw, orbit.pitch, hoveredFace)
        cube
            ?.takeIf { it.first == key }
            ?.let {
                return it.second
            }
        return GpuCanvasFrame(emptyList(), ViewCube(orbit.yaw, orbit.pitch).mesh(hoveredFace))
            .also {
                cube = key to it
            }
    }

    /** The cube patch under ([x], [y]) on the cube's canvas lights up; null clears it. */
    fun hoverCube(x: Double?, y: Double?) {
        hoveredFace = if (x == null || y == null) null else viewCube().targetAt(x, y)
    }

    /** A click on the cube: glide to look from what was clicked. */
    fun clickCube(x: Double, y: Double) = update {
        val target = viewCube().targetAt(x, y) ?: return@update
        if (target.isTop) orbit.lookDown() else orbit.lookFrom(target.yaw, target.pitch)
    }

    /** Dragging the cube turns and tilts the map with it. */
    fun orbitBy(dx: Double, dy: Double) = update {
        if (terrain == null) return@update
        orbit.dragBy(-dx * ORBIT_DEGREES_PER_PIXEL, dy * ORBIT_DEGREES_PER_PIXEL)
    }

    fun endOrbit() = orbit.settle()

    fun turnQuarters(quarters: Int) = update { if (terrain != null) orbit.turnQuarters(quarters) }

    /** Between the flat map and the isometric look from the south-east corner. */
    fun toggle3d() = update {
        if (terrain == null) return@update
        if (orbit.isFlat) orbit.lookFrom(ISOMETRIC_YAW, MapOrbit.ISOMETRIC_PITCH)
        else {
            orbit.lookDown()
            orbit.cutAt(null)
        }
    }

    /**
     * Cuts the world above [y], or shows it whole for null or the top of [sliceRange]. Straight
     * down a cut is a map of the caves and floors under that height.
     */
    fun sliceAt(y: Int?) = update {
        if (terrain == null) return@update
        val cut = y?.coerceIn(sliceRange)?.takeIf { it < sliceRange.last }
        orbit.cutAt(cut)
    }

    fun stepSlice(notches: Int) = sliceAt((orbit.slice ?: sliceRange.last) + notches)

    private fun viewCube() = ViewCube(orbit.yaw, orbit.pitch)

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
        if (!flat) {
            val iso = isoCamera(width, height)
            val arrow =
                GpuImageDraw(
                    PlayerMarker.image,
                    (iso.screenX(self.x, self.z) - size / 2).toFloat(),
                    (iso.screenY(self.x, self.y, self.z) - size / 2).toFloat(),
                    size,
                    size,
                    PlayerMarker.rotation(client.playerYaw ?: 0f) + orbit.yaw.toFloat(),
                )
            val dots =
                if (mapTime == MapTime.Live) entities.draws(iso, position.y, seconds)
                else emptyList<GpuImageDraw>().also { entities.clear() }
            return MapOverlay(GpuCanvasFrame(dots), GpuCanvasFrame(listOf(arrow)))
        }
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
        val seconds = orbitClock.tick(frameNanos)
        val turned = orbit.advance(seconds)
        val rose = !flat && followGround(seconds)
        if (moved || glided || turned || rose) publish()
        if (mapTime == MapTime.Live && ClaimLayer.enabled.value)
            session.requestClaims(camera.camera(width, height))
    }

    /** The 3D view's middle follows the ground under it, so turning pivots on what is looked at. */
    private fun followGround(seconds: Double): Boolean {
        val ground =
            terrain?.field?.topAt(floor(camera.centerX).toInt(), floor(camera.centerZ).toInt())
                ?: return false
        val target = (orbit.slice?.let { minOf(it, ground) } ?: ground).toDouble()
        if (abs(target - centerY) < ARRIVED_BLOCKS) {
            if (centerY == target) return false
            centerY = target
            return true
        }
        centerY += (target - centerY) * easeStep(seconds, GROUND_GLIDE_SECONDS)
        return true
    }

    fun dragBy(dx: Double, dy: Double, nowNanos: Long) = update {
        if (flat) camera.dragBy(dx, dy, nowNanos)
        else orbit.toFlat(dx, dy).let { (x, z) -> camera.dragBy(x, z, nowNanos) }
    }

    fun endDrag(nowNanos: Long) = camera.endDrag(nowNanos)

    fun panBy(dx: Double, dy: Double) =
        if (flat) camera.panBy(dx, dy)
        else orbit.toFlat(dx, dy).let { (x, z) -> camera.panBy(x, z) }

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
        val nearby = waypointAt(width, height, screenX, screenY)
        if (nearby != null) {
            mutableWaypointEditor.value = WaypointEditorModel.from(nearby)
            mutableWaypointListOpen.value = false
            return
        }
        val (x, z) = groundAt(width, height, screenX, screenY)
        mutableWaypointEditor.value = WaypointEditorModel.new(x, surfaceYAt(x, z), z)
        mutableWaypointListOpen.value = false
    }

    fun editWaypointAt(screenX: Double, screenY: Double, width: Int, height: Int): Boolean {
        if (mapTime != MapTime.Live) return false
        val waypoint = waypointAt(width, height, screenX, screenY) ?: return false
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

    private fun waypointAt(width: Int, height: Int, x: Double, y: Double): Waypoint? {
        val project = projection(width, height)
        return session.waypoints.entries.value.firstOrNull { waypoint ->
            val (screenX, screenY) =
                project(waypoint.x + 0.5, waypoint.y.toDouble(), waypoint.z + 0.5)
            abs(screenX - x) <= WAYPOINT_HIT_RADIUS && abs(screenY - y) <= WAYPOINT_HIT_RADIUS
        }
    }

    /** World to canvas for whichever view is showing; the flat map ignores height. */
    private fun projection(
        width: Int,
        height: Int,
    ): (Double, Double, Double) -> Pair<Double, Double> {
        if (flat) {
            val camera = camera.camera(width, height)
            return { x, _, z -> camera.screenAt(x, z) }
        }
        val iso = isoCamera(width, height)
        return { x, y, z -> iso.screenX(x, z) to iso.screenY(x, y, z) }
    }

    /**
     * The block column under a canvas point. In 3D the ray down from the eye is walked onto the
     * columns: a few rounds of finding the ground at the height the last round stood on.
     */
    private fun groundAt(
        width: Int,
        height: Int,
        screenX: Double,
        screenY: Double,
    ): Pair<Int, Int> {
        if (flat) {
            val (x, z) = camera.camera(width, height).worldAt(screenX, screenY)
            return floor(x).toInt() to floor(z).toInt()
        }
        val iso = isoCamera(width, height)
        var y = centerY
        var column = 0 to 0
        repeat(PICK_ROUNDS) {
            val (x, z) = iso.groundAt(screenX, screenY, y)
            column = floor(x).toInt() to floor(z).toInt()
            y = terrain?.field?.topAt(column.first, column.second)?.toDouble() ?: return column
        }
        return column
    }

    private fun surfaceYAt(x: Int, z: Int): Int {
        val tile = session.map.store.latestTile(TileKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)))
        val cell = Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)
        return tile?.height(cell)
            ?: ClientBackend.current.playerPosition?.y?.toInt()
            ?: session.ceiling
    }

    fun zoomBy(steps: Double, atX: Double, atY: Double) {
        if (flat) return camera.zoomBy(steps, atX, atY)
        // Zooming about the ground at the view's height keeps that ground under the cursor in 3D
        // too.
        val (x, z) = orbit.toFlat(atX - viewportWidth / 2.0, atY - viewportHeight / 2.0)
        camera.zoomBy(steps, viewportWidth / 2.0 + x, viewportHeight / 2.0 + z)
    }

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
            view =
                terrain?.let {
                    MapViewAngle(orbit.yaw, orbit.pitch, centerY, orbit.slice, sliceRange, flat)
                },
        )

    private companion object {
        const val WAYPOINT_HIT_RADIUS = 10.0
        const val TILE_BLOCKS = 16.0
        /** Enough to outline a big commit; beyond it the flash would cost more than it tells. */
        const val MAX_HIGHLIGHT_TILES = 4096
        /** How much of the viewport the changed area fills after flying to it. */
        const val FIT_FRACTION = 0.6
        const val MAX_FIT_PIXELS_PER_BLOCK = 2.0
        const val MAX_ORBIT_SECONDS = 0.1
        const val ORBIT_DEGREES_PER_PIXEL = 0.6
        /** The isometric look from the south-east, the cube's nearest corner at rest. */
        const val ISOMETRIC_YAW = 45.0
        const val GROUND_GLIDE_SECONDS = 0.15
        const val ARRIVED_BLOCKS = 0.05
        const val PICK_ROUNDS = 4
        /** Worlds that reach above this reach below zero too, down to [MODERN_BOTTOM]. */
        const val VANILLA_TOP = 255
        const val MODERN_BOTTOM = -64
    }
}
